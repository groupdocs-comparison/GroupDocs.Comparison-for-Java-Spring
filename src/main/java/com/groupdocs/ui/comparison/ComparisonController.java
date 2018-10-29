package com.groupdocs.ui.comparison;

import com.google.common.collect.Lists;
import com.groupdocs.ui.comparison.model.request.CompareFileDataRequest;
import com.groupdocs.ui.comparison.model.request.CompareRequest;
import com.groupdocs.ui.comparison.model.request.LoadResultPageRequest;
import com.groupdocs.ui.comparison.model.response.CompareResultResponse;
import com.groupdocs.ui.config.GlobalConfiguration;
import com.groupdocs.ui.exception.TotalGroupDocsException;
import com.groupdocs.ui.model.request.FileTreeRequest;
import com.groupdocs.ui.model.response.FileDescriptionEntity;
import com.groupdocs.ui.model.response.LoadedPageEntity;
import com.groupdocs.ui.model.response.UploadedDocumentEntity;
import com.groupdocs.ui.util.Utils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.groupdocs.ui.util.Utils.uploadFile;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Controller
@RequestMapping("/comparison")
public class ComparisonController {
    private static final Logger logger = LoggerFactory.getLogger(ComparisonController.class);

    @Autowired
    private GlobalConfiguration globalConfiguration;

    @Autowired
    private ComparisonService comparisonService;

    /**
     * Get comparison page
     * @param model model data for template
     * @return template name
     */
    @RequestMapping(method = RequestMethod.GET)
    public String getView(Map<String, Object> model) {
        model.put("globalConfiguration", globalConfiguration);
        logger.debug("comparison config: {}", comparisonService.getComparisonConfiguration());
        model.put("comparisonConfiguration", comparisonService.getComparisonConfiguration());
        return "comparison";
    }

    /**
     * Get files and directories
     * @return files and directories list
     */
    @RequestMapping(method = RequestMethod.POST, value = "/loadFileTree", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<FileDescriptionEntity> loadFileTree(@RequestBody FileTreeRequest fileTreeRequest){
        return comparisonService.loadFiles(fileTreeRequest);
    }

    /**
     * Download results
     *
     * @param documentGuid unique key of results
     * @param index page number of result images
     * @param ext results file extension
     */
    @RequestMapping(method = RequestMethod.GET, value = "/downloadDocument")
    public void downloadDocument(@RequestParam(name = "guid") String documentGuid,
                                 @RequestParam(name = "index", required = false) Integer index,
                                 @RequestParam(name = "ext", required = false) String ext,
                                 HttpServletResponse response) {
        String filePath = comparisonService.calculateResultFileName(documentGuid, index, ext);
        File file = new File(filePath);
        // set response content info
        Utils.addFileDownloadHeaders(response, file.getName(), file.length());
        // download the document
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(filePath));
             ServletOutputStream outputStream = response.getOutputStream()) {

            IOUtils.copyLarge(inputStream, outputStream);
        } catch (Exception ex){
            logger.error("Exception in downloading document", ex);
            throw new TotalGroupDocsException(ex.getMessage(), ex);
        }
    }

    /**
     * Upload document
     * @return uploaded document object (the object contains uploaded document guid)
     */
    @RequestMapping(method = RequestMethod.POST, value = "/uploadDocument",
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public UploadedDocumentEntity uploadDocument(@Nullable @RequestParam("file") MultipartFile content,
                                                 @RequestParam(value = "url", required = false) String url,
                                                 @RequestParam("rewrite") Boolean rewrite) {
        // get documents storage path
        String documentStoragePath = comparisonService.getComparisonConfiguration().getFilesDirectory();
        // save the file
        String pathname = uploadFile(documentStoragePath, content, url, rewrite);
        // create response data
        UploadedDocumentEntity uploadedDocument = new UploadedDocumentEntity();
        uploadedDocument.setGuid(pathname);
        return uploadedDocument;
    }

    /**
     * Compare files from local storage
     *
     * @param compareRequest request with paths to files
     * @return response with compare results
     */
    @RequestMapping(method = RequestMethod.POST, value = "/compareWithPaths", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompareResultResponse compareWithPaths(@RequestBody CompareRequest compareRequest) {
        // check formats
        if (comparisonService.checkFiles(compareRequest.getFirstPath(), compareRequest.getSecondPath())) {
            // compare
            return comparisonService.compare(compareRequest);
        } else {
            logger.error("Document types are different");
            throw new TotalGroupDocsException("Document types are different");
        }
    }

    /**
     * Compare documents from form formats
     *
     * @param firstContent content of first file
     * @param secondContent content of second file
     * @param firstPassword password for first file
     * @param secondPassword password for second file
     * @return response with compare results
     */
    @RequestMapping(method = RequestMethod.POST, value = "/compareFiles",
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompareResultResponse compareFiles(@RequestParam("firstFile") MultipartFile firstContent,
                                              @RequestParam("secondFile") MultipartFile secondContent,
                                              @RequestParam("firstPassword") String firstPassword,
                                              @RequestParam("secondPassword") String secondPassword) {
        try {
            String firstFileName = firstContent.getOriginalFilename();
            String secondFileName = secondContent.getOriginalFilename();
            // check formats
            if (comparisonService.checkFiles(firstFileName, secondFileName)) {
                InputStream firstInputStream = firstContent.getInputStream();
                InputStream secondInputStream = secondContent.getInputStream();
                // compare files
                return comparisonService.compareFiles(firstInputStream, firstPassword, secondInputStream, secondPassword, FilenameUtils.getExtension(firstFileName));
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while compare files by input streams.");
            throw new TotalGroupDocsException("Exception occurred while compare files by input streams.", e);
        }
    }

    /**
     * Compare two files by urls
     *
     * @param compareRequest request with urls to files
     * @return response with compare results
     */
    @RequestMapping(method = RequestMethod.POST, value = "/compareWithUrls", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompareResultResponse compareWithUrls(@RequestBody CompareRequest compareRequest) {
        try {
            String firstPath = compareRequest.getFirstPath();
            String secondPath = compareRequest.getSecondPath();
            // check formats
            if (comparisonService.checkFiles(firstPath, secondPath)) {
                URL fUrl = URI.create(firstPath).toURL();
                URL sUrl = URI.create(secondPath).toURL();

                String firstPassword = compareRequest.getFirstPassword();
                String secondPassword = compareRequest.getSecondPassword();
                // open streams for urls
                try (InputStream firstContent = fUrl.openStream();
                     InputStream secondContent = sUrl.openStream()) {
                    // compare
                    return comparisonService.compareFiles(firstContent, firstPassword, secondContent, secondPassword, FilenameUtils.getExtension(firstPath));
                }
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while compare files by urls.");
            throw new TotalGroupDocsException("Exception occurred while compare files by urls.", e);
        }
    }

    /**
     * Get result page
     * @return result page image
     */
    @RequestMapping(method = RequestMethod.POST, value = "/loadResultPage", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public LoadedPageEntity loadResultPage(@RequestBody LoadResultPageRequest loadResultPageRequest){
        return comparisonService.loadResultPage(loadResultPageRequest);
    }

    /**
     * Compare 2 files got by different ways
     *
     * @param files files data
     * @param passwords files passwords
     * @param urls files url and password
     * @param paths files path and password
     * @return response with compare results
     */
    @RequestMapping(method = RequestMethod.POST, value = "/compare",
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompareResultResponse compare(@RequestPart("files") MultipartFile[] files,
                                         @RequestPart("passwords") List<String> passwords,
                                         @RequestPart("urls") List<CompareFileDataRequest> urls,
                                         @RequestPart("paths") List<CompareFileDataRequest> paths) {
        // calculate total amount of files
        int initialCapacity = files.length + urls.size() + paths.size();

        if (initialCapacity != 2) {
            throw new TotalGroupDocsException("Comparing is impossible. There are must be 2 files.");
        }
        try {
            // transform all files into input streams
            TransformFiles transformFiles = new TransformFiles(Lists.newArrayList(files), passwords, urls, paths, initialCapacity).transformToStreams();
            List<String> fileNames = transformFiles.getFileNames();

            // check formats
            if (comparisonService.checkMultiFiles(fileNames)) {
                // get file extension
                String ext = FilenameUtils.getExtension(fileNames.get(0));

                // compare
                List<InputStream> newFiles = transformFiles.getNewFiles();
                List<String> newPasswords = transformFiles.getNewPasswords();
                return comparisonService.compareFiles(newFiles.get(0), newPasswords.get(0), newFiles.get(1), newPasswords.get(1), ext);
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while compare files.");
            throw new TotalGroupDocsException("Exception occurred while compare files.", e);
        }
    }

    /**
     * Compare several files got by different ways
     *
     * @param files files data
     * @param passwords files passwords
     * @param urls files url and password
     * @param paths files path and password
     * @return response with compare results
     */
    @RequestMapping(method = RequestMethod.POST, value = "/multiCompare",
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompareResultResponse multiCompare(@RequestPart("files") MultipartFile[] files,
                                              @RequestPart("passwords") List<String> passwords,
                                              @RequestPart("urls") List<CompareFileDataRequest> urls,
                                              @RequestPart("paths") List<CompareFileDataRequest> paths) {
        // calculate total amount of files
        int initialCapacity = files.length + urls.size() + paths.size();

        if (initialCapacity < 2) {
            throw new TotalGroupDocsException("Comparing is impossible. There are less than 2 files.");
        }
        try {
            // transform all files into input streams
            TransformFiles transformFiles = new TransformFiles(Lists.newArrayList(files), passwords, urls, paths, initialCapacity).transformToStreams();
            List<String> fileNames = transformFiles.getFileNames();

            // check formats
            if (comparisonService.checkMultiFiles(fileNames)) {
                // get file extension
                String ext = FilenameUtils.getExtension(fileNames.get(0));

                // compare
                return comparisonService.multiCompareFiles(transformFiles.getNewFiles(), transformFiles.getNewPasswords(), ext);
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while multi compare files by streams.");
            throw new TotalGroupDocsException("Exception occurred while multi compare files by streams.", e);
        }
    }

    private class TransformFiles {
        private List<MultipartFile> files;
        private List<String> passwords;
        private List<CompareFileDataRequest> urls;
        private List<CompareFileDataRequest> paths;
        private int initialCapacity;
        private List<InputStream> newFiles;
        private List<String> fileNames;
        private List<String> newPasswords;

        public TransformFiles(List<MultipartFile> files, List<String> passwords, List<CompareFileDataRequest> urls, List<CompareFileDataRequest> paths, int initialCapacity) {
            this.files = files;
            this.passwords = passwords;
            this.urls = urls;
            this.paths = paths;
            this.initialCapacity = initialCapacity;
        }

        public List<InputStream> getNewFiles() {
            return newFiles;
        }

        public List<String> getFileNames() {
            return fileNames;
        }

        public List<String> getNewPasswords() {
            return newPasswords;
        }

        public TransformFiles transformToStreams() throws IOException {
            newFiles = new ArrayList<>(initialCapacity);
            fileNames = new ArrayList<>(initialCapacity);
            newPasswords = new ArrayList<>(initialCapacity);

            // transform MultipartFile
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                fileNames.add(file.getOriginalFilename());
                newFiles.add(file.getInputStream());
                newPasswords.add(passwords.get(i));
            }

            // transform urls
            for (CompareFileDataRequest urlRequest: urls) {
                String file = urlRequest.getFile();
                fileNames.add(file);
                URL url = URI.create(file).toURL();
                newFiles.add(url.openStream());
                newPasswords.add(urlRequest.getPassword());
            }

            // transform paths
            for (CompareFileDataRequest pathRequest: paths) {
                String file = pathRequest.getFile();
                fileNames.add(file);
                newFiles.add(new FileInputStream(file));
                newPasswords.add(pathRequest.getPassword());
            }
            return this;
        }
    }
}

package com.groupdocs.ui.comparison;

import com.groupdocs.comparison.Comparer;
import com.groupdocs.comparison.common.delegates.Delegates;
import com.groupdocs.comparison.interfaces.IDocumentInfo;
import com.groupdocs.comparison.license.License;
import com.groupdocs.comparison.options.CompareOptions;
import com.groupdocs.comparison.options.PreviewOptions;
import com.groupdocs.comparison.options.enums.PreviewFormats;
import com.groupdocs.comparison.options.load.LoadOptions;
import com.groupdocs.comparison.options.style.DetalisationLevel;
import com.groupdocs.comparison.result.ChangeInfo;
import com.groupdocs.comparison.utils.common.Path;
import com.groupdocs.ui.comparison.model.request.CompareRequest;
import com.groupdocs.ui.comparison.model.response.CompareResultResponse;
import com.groupdocs.ui.config.DefaultDirectories;
import com.groupdocs.ui.config.GlobalConfiguration;
import com.groupdocs.ui.exception.TotalGroupDocsException;
import com.groupdocs.ui.model.request.FileTreeRequest;
import com.groupdocs.ui.model.request.LoadDocumentPageRequest;
import com.groupdocs.ui.model.request.LoadDocumentRequest;
import com.groupdocs.ui.model.response.FileDescriptionEntity;
import com.groupdocs.ui.model.response.LoadDocumentEntity;
import com.groupdocs.ui.model.response.PageDescriptionEntity;
import com.groupdocs.ui.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import static com.groupdocs.ui.util.Utils.parseFileExtension;

@Service
public class ComparisonServiceImpl implements ComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(ComparisonServiceImpl.class);
    public static final String TEMP_DIRECTORY_NAME = "temp";

    @Autowired
    private ComparisonConfiguration comparisonConfiguration;
    @Autowired
    private GlobalConfiguration globalConfiguration;

    /**
     * Initializing fields after creating configuration objects
     */
    @PostConstruct
    public void init() {
        // check files directories
        String filesDirectory = comparisonConfiguration.getFilesDirectory();
        String resultDirectory = comparisonConfiguration.getResultDirectory();
        if (StringUtils.isEmpty(resultDirectory)) {
            resultDirectory = filesDirectory + File.separator + TEMP_DIRECTORY_NAME;
            comparisonConfiguration.setResultDirectory(resultDirectory);
        }
        DefaultDirectories.makeDirs(Paths.get(resultDirectory));
        // set GroupDocs license
        try {
            License license = new License();
            license.setLicense(globalConfiguration.getApplication().getLicensePath());
        } catch (Throwable exc) {
            logger.error("Can not verify Comparison license!");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComparisonConfiguration getComparisonConfiguration() {
        return comparisonConfiguration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileDescriptionEntity> loadFiles(FileTreeRequest fileTreeRequest) {
        final String path = fileTreeRequest.getPath();
        final File filesDirectory = new File(Path.combine(comparisonConfiguration.getFilesDirectory(), path));

        List<FileDescriptionEntity> filesList = new ArrayList<>();
        try {
            final File[] files = filesDirectory.listFiles();
            if (files == null) {
                throw new TotalGroupDocsException("Can't list files");
            }

            for (File file : files) {
                // check if current file/folder is hidden
                if (!(file.getName().equals(comparisonConfiguration.getFilesDirectory())) && !(file.getName().equals(comparisonConfiguration.getResultDirectory())) && !file.getName().startsWith(".") && !file.isHidden() && !TEMP_DIRECTORY_NAME.equals(file.getName())) {
                    FileDescriptionEntity fileDescription = new FileDescriptionEntity();
                    fileDescription.setGuid(file.getCanonicalFile().getAbsolutePath());
                    fileDescription.setName(file.getName());
                    // set is directory true/false
                    fileDescription.setIsDirectory(file.isDirectory());

                    // set file size
                    if (!fileDescription.isIsDirectory()) {
                        fileDescription.setSize(file.length());
                    }

                    // add object to array list
                    filesList.add(fileDescription);
                }
            }

            // Sort by name and to make directories to be before files
            Collections.sort(filesList, new Comparator<FileDescriptionEntity>() {
                @Override
                public int compare(FileDescriptionEntity o1, FileDescriptionEntity o2) {
                    if (o1.isIsDirectory() && !o2.isIsDirectory()) {
                        return -1;
                    }
                    if (!o1.isIsDirectory() && o2.isIsDirectory()) {
                        return 1;
                    }
                    return o1.getName().compareTo(o2.getName());
                }
            });
        } catch (IOException e) {
            logger.error("Exception in getting file list", e);
            throw new TotalGroupDocsException(e.getMessage(), e);
        }
        return filesList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompareResultResponse compare(CompareRequest compareRequest) {
        CompareResultResponse compareResultResponse = null;
        try {
            compareResultResponse = compareTwoDocuments(compareRequest);
        } catch (FileNotFoundException e) {
            throw new TotalGroupDocsException(e.getMessage(), e);
        }
        return compareResultResponse;
    }

    @Override
    public LoadDocumentEntity loadDocumentDescription(LoadDocumentPageRequest loadDocumentPageRequest) {
        final String documentGuid = loadDocumentPageRequest.getGuid();
        final String password = loadDocumentPageRequest.getPassword();
        return loadDocumentPages(documentGuid, password, comparisonConfiguration.getPreloadResultPageCount() == 0);
    }

    private CompareResultResponse compareTwoDocuments(CompareRequest compareRequest) throws FileNotFoundException {
        // to get correct coordinates we will compare document twice
        // this is a first comparing to get correct coordinates of the insertions and style changes
        String extension = "." + Utils.parseFileExtension(compareRequest.getGuids().get(0).getGuid());
        String guid = UUID.randomUUID().toString();
        //save all results in file
        String resultGuid = Path.combine(comparisonConfiguration.getResultDirectory(), guid + extension);

        Comparer compareResult = compareFiles(compareRequest, resultGuid);
        ChangeInfo[] changes = compareResult.getChanges();

        CompareResultResponse compareResultResponse = getCompareResultResponse(changes, resultGuid);
        compareResultResponse.setExtension(extension);
        return compareResultResponse;
    }

    private CompareResultResponse getCompareResultResponse(ChangeInfo[] changes, String resultGuid) {
        CompareResultResponse compareResultResponse = new CompareResultResponse();
        compareResultResponse.setChanges(changes);

        List<PageDescriptionEntity> pages = loadDocumentPages(resultGuid, "", true).getPages();

        compareResultResponse.setPages(pages);
        compareResultResponse.setGuid(resultGuid);
        return compareResultResponse;
    }

    public static LoadDocumentEntity loadDocumentPages(String documentGuid, String password, boolean loadAllPages) {
        LoadDocumentEntity loadDocumentEntity = new LoadDocumentEntity();

        Comparer comparer = new Comparer(documentGuid, getLoadOptions(password));
        try {
            Map<Integer, String> pagesContent = new HashMap<>();
            IDocumentInfo documentInfo = comparer.getSource().getDocumentInfo();

            if (loadAllPages) {
                for (int i = 0; i < documentInfo.getPageCount(); i++) {
                    String encodedImage = getPageData(i, documentGuid, password);

                    pagesContent.put(i, encodedImage);
                }
            }

            for (int i = 0; i < documentInfo.getPageCount(); i++) {
                PageDescriptionEntity pageData = new PageDescriptionEntity();
                pageData.setHeight(800 /*documentInfo.getPagesInfo().get(i).getHeight()*/); // Uncomment in v20.10
                pageData.setWidth(600 /*documentInfo.getPagesInfo().get(i).getWidth()*/); // Uncomment in v20.10
                pageData.setNumber(i + 1);

                if (pagesContent.size() > 0) {
                    pageData.setData(pagesContent.get(i));
                }

                loadDocumentEntity.getPages().add(pageData);
            }

            return loadDocumentEntity;
        } finally {
            comparer.dispose();
        }
    }

    private static LoadOptions getLoadOptions(String password) {
        LoadOptions loadOptions = new LoadOptions();
        loadOptions.setPassword(password);

        return loadOptions;
    }

    private static String getPageData(int pageNumber, String documentGuid, String password) {
        String encodedImage = "";

        Comparer comparer = new Comparer(documentGuid, getLoadOptions(password));
        try {
            byte[] bytes = renderPageToMemoryStream(comparer, pageNumber);
            encodedImage = new String(Base64.getEncoder().encode(bytes)).replace("\n", "");
        } finally {
            comparer.dispose();
        }

        return encodedImage;
    }

    static byte[] renderPageToMemoryStream(Comparer comparer, int pageNumberToRender) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        IDocumentInfo documentInfo = comparer.getSource().getDocumentInfo();

        PreviewOptions previewOptions = new PreviewOptions(new Delegates.CreatePageStream() {
            @Override
            public OutputStream invoke(int i) {
                return result;
            }
        });

        previewOptions.setPreviewFormat(PreviewFormats.PNG);
        previewOptions.setPageNumbers(new int[]{pageNumberToRender + 1});
        previewOptions.setHeight(800 /*documentInfo.getPagesInfo().get(pageNumberToRender).getHeight()*/); // Uncomment in v20.10
        previewOptions.setWidth(600 /*documentInfo.getPagesInfo().get(pageNumberToRender).getWidth()*/); // Uncomment in v20.10
        try {
            comparer.getSource().generatePreview(previewOptions);
            return result.toByteArray();
        } finally {
            com.groupdocs.comparison.common.Utils.closeStreams(result);
        }


    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public PageDescriptionEntity loadDocumentPage(LoadDocumentPageRequest loadDocumentPageRequest) {
        final String documentGuid = loadDocumentPageRequest.getGuid();
        final String password = loadDocumentPageRequest.getPassword();
        final Integer pageNumber = loadDocumentPageRequest.getPage();

        PageDescriptionEntity loadedPage = new PageDescriptionEntity();

        Comparer comparer = new Comparer(documentGuid, getLoadOptions(password));
        try {
            IDocumentInfo info = comparer.getSource().getDocumentInfo();

            String encodedImage = getPageData(pageNumber - 1, documentGuid, password);
            loadedPage.setData(encodedImage);

            loadedPage.setHeight(800 /*info.getPagesInfo().get(pageNumber - 1).getHeight()*/); // Uncomment in v20.10
            loadedPage.setWidth(600 /*info.getPagesInfo().get(pageNumber - 1).getWidth()*/); // Uncomment in v20.10
            loadedPage.setNumber(pageNumber);
        } catch (Exception ex) {
            throw new TotalGroupDocsException("Exception occurred while loading result page", ex);
        } finally {
            comparer.dispose();
        }

        return loadedPage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkFiles(CompareRequest request) {
        List<LoadDocumentRequest> guids = request.getGuids();
        LoadDocumentRequest loadDocumentRequestFirst = guids.get(0);
        LoadDocumentRequest loadDocumentRequestSecond = guids.get(1);
        String sourceExtension = parseFileExtension(loadDocumentRequestFirst.getGuid());
        String targetExtension = parseFileExtension(loadDocumentRequestSecond.getGuid());
        assert sourceExtension != null : "sourceExtension is null";
        assert targetExtension != null : "targetExtension is null";
        // check if files extensions are the same and support format file
        return Objects.equals(sourceExtension, targetExtension) && checkSupportedFiles(sourceExtension.toLowerCase());
    }

    /**
     * Check support formats for comparing
     *
     * @param extension file extension
     * @return true - format is supported, false - format is not supported
     */
    private boolean checkSupportedFiles(String extension) {
        switch (extension) {
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
            case "pdf":
            case "txt":
            case "html":
            case "htm":
            case "jpg":
            case "jpeg":
                return true;
            default:
                return false;
        }
    }

    private static Comparer compareFiles(CompareRequest compareRequest, String resultGuid) throws FileNotFoundException {
        String firstPath = compareRequest.getGuids().get(0).getGuid();
        String secondPath = compareRequest.getGuids().get(1).getGuid();

        // create new comparer
        Comparer comparer = new Comparer(firstPath, getLoadOptions(compareRequest.getGuids().get(0).getPassword()));

        comparer.add(secondPath, getLoadOptions(compareRequest.getGuids().get(1).getPassword()));
        CompareOptions compareOptions = new CompareOptions();
        compareOptions.setCalculateCoordinates(true);

        if ("pdf".equals(Utils.parseFileExtension(resultGuid))) {
            compareOptions.setDetalisationLevel(DetalisationLevel.High);
        }
        OutputStream outputStream = new FileOutputStream(resultGuid);
        try {
            comparer.compare(outputStream, compareOptions);
        } finally {
            com.groupdocs.comparison.common.Utils.closeStreams(outputStream);
        }

        return comparer;
    }
}

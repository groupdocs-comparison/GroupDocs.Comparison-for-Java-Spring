package com.groupdocs.ui.comparison;

import com.google.common.collect.Ordering;
import com.groupdocs.comparison.Comparer;
import com.groupdocs.comparison.common.PageImage;
import com.groupdocs.comparison.common.TypeChanged;
import com.groupdocs.comparison.common.changes.ChangeInfo;
import com.groupdocs.comparison.common.compareresult.ICompareResult;
import com.groupdocs.comparison.common.comparisonsettings.ComparisonSettings;
import com.groupdocs.comparison.common.license.License;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.groupdocs.ui.util.Utils.*;

@Service
public class ComparisonServiceImpl implements ComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(ComparisonServiceImpl.class);
    public static final String DOCX = "docx";
    public static final String DOC = "doc";
    public static final String XLS = "xls";
    public static final String XLSX = "xlsx";
    public static final String PPT = "ppt";
    public static final String PPTX = "pptx";
    public static final String PDF = "pdf";
    public static final String TXT = "txt";
    public static final String HTM = "htm";
    public static final String HTML = "html";
    public static final String TEMP_HTML = "temp.html";

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
            resultDirectory = filesDirectory + File.separator + "Temp";
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
        String currentPath = fileTreeRequest.getPath();
        if (StringUtils.isEmpty(currentPath)) {
            currentPath = comparisonConfiguration.getFilesDirectory();
        } else {
            currentPath = String.format("%s%s%s", comparisonConfiguration.getFilesDirectory(), File.separator, currentPath);
        }
        File directory = new File(currentPath);
        List<FileDescriptionEntity> fileList = new ArrayList<>();
        List<File> filesList = Ordering.from(FILE_TYPE_COMPARATOR).compound(FILE_NAME_COMPARATOR)
                .sortedCopy(Arrays.asList(directory.listFiles()));
        try {
            for (File file : filesList) {
                if (!file.isHidden()) {
                    FileDescriptionEntity fileDescription = getFileDescriptionEntity(file);
                    fileList.add(fileDescription);
                }
            }
            return fileList;
        } catch (Exception ex) {
            logger.error("Exception occurred while load file tree");
            throw new TotalGroupDocsException(ex.getMessage(), ex);
        }
    }

    /**
     * Create file description
     *
     * @param file file
     * @return file description
     */
    private FileDescriptionEntity getFileDescriptionEntity(File file) {
        FileDescriptionEntity fileDescription = new FileDescriptionEntity();
        // set path to file
        fileDescription.setGuid(file.getAbsolutePath());
        // set file name
        fileDescription.setName(file.getName());
        // set is directory true/false
        fileDescription.setDirectory(file.isDirectory());
        // set file size
        fileDescription.setSize(file.length());
        return fileDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompareResultResponse compare(CompareRequest compareRequest) {
        List<LoadDocumentRequest> guids = compareRequest.getGuids();
        LoadDocumentRequest loadDocumentRequestFirst = guids.get(0);
        LoadDocumentRequest loadDocumentRequestSecond = guids.get(1);
        String firstPath = loadDocumentRequestFirst.getGuid();

        ICompareResult compareResult, compareResultRevers;

        //TODO: remove this synchronization when the bug COMPARISONJAVA-436 is fixed
        synchronized (this) {
            compareResult = compareFiles(loadDocumentRequestFirst, loadDocumentRequestSecond);
            compareResultRevers = compareFiles(loadDocumentRequestSecond, loadDocumentRequestFirst);
        }

        String extension = parseFileExtension(firstPath);
        CompareResultResponse compareResultResponse = null;
        try {
            compareResultResponse = getCompareResultResponse(extension, compareResult, compareResultRevers);
        } catch (Exception e) {
            throw new TotalGroupDocsException(e.getMessage());
        }

        return compareResultResponse;
    }

    protected CompareResultResponse getCompareResultResponse(String fileExt, ICompareResult compareResult, ICompareResult compareResultRevers) throws Exception {
        if (compareResult == null || compareResultRevers == null) {
            throw new TotalGroupDocsException("Something went wrong. We've got null result.");
        }

        boolean isHtml = HTML.equals(fileExt) || HTM.equals(fileExt);
        CompareResultResponse compareResultResponse = createCompareResultResponse(compareResult, compareResultRevers, isHtml);

        String guid = UUID.randomUUID().toString();
        String savedFile = saveFile(guid, compareResult.getStream(), fileExt);
        compareResultResponse.setGuid(savedFile);
        compareResultResponse.setExtension(fileExt);

        compareResultResponse.setPages(loadPages(savedFile, null));

        return compareResultResponse;
    }

    /**
     * Convert results of comparing and save result files
     *
     * @param compareResult results
     * @param isHtml
     * @return results response
     */
    private CompareResultResponse createCompareResultResponse(ICompareResult compareResult, ICompareResult compareResultRevers, boolean isHtml) throws Exception {
        CompareResultResponse compareResultResponse = new CompareResultResponse();

        List<ChangeInfo> changeInfoList = new ArrayList<>();
        ChangeInfo[] changes = compareResult.getChanges();
        addChanges(changeInfoList, changes);
        ChangeInfo[] reversChanges = compareResultRevers.getChanges();
        addReversChanges(changeInfoList, reversChanges);

        compareResultResponse.setChanges(changeInfoList.toArray(new ChangeInfo[changeInfoList.size()]));

        if (isHtml) {
            String resultDirectory = getResultDirectory();
            compareResult.saveDocument(resultDirectory + File.separator + TEMP_HTML);
        }

        return compareResultResponse;
    }

    private void addChanges(List<ChangeInfo> changeInfoList, ChangeInfo[] changes) {
        for (int i = 0; i < changes.length; i++) {
            ChangeInfo change = changes[i];
            if (TypeChanged.Deleted != change.getType()) {
                changeInfoList.add(change);
            }
        }
    }

    private void addReversChanges(List<ChangeInfo> changeInfoList, ChangeInfo[] reversChanges) {
        for (int i = 0; i < reversChanges.length; i++) {
            ChangeInfo reversChange = reversChanges[i];
            if (TypeChanged.Inserted == reversChange.getType()) {
                reversChange.setType(TypeChanged.Deleted);
                changeInfoList.add(reversChange);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageDescriptionEntity loadResultPage(LoadDocumentPageRequest loadDocumentPageRequest) {
        try {
            Comparer comparer = new Comparer();
            List<PageImage> pageImages = comparer.convertToImages(loadDocumentPageRequest.getGuid(), loadDocumentPageRequest.getPassword());
            try {
                PageImage pageImage = pageImages.get(loadDocumentPageRequest.getPage() - 1);
                return getPageDescriptionEntity(pageImage);
            } catch (Exception ex) {
                logger.error("Exception occurred while loading result page", ex);
                throw new TotalGroupDocsException("Exception occurred while loading result page", ex);
            }
        } catch (Exception ex) {
            throw new TotalGroupDocsException(getExceptionMessage(loadDocumentPageRequest.getPassword(), ex), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkFiles(CompareRequest request) {
        List<LoadDocumentRequest> guids = request.getGuids();
        LoadDocumentRequest loadDocumentRequestFirst = guids.get(0);
        LoadDocumentRequest loadDocumentRequestSecond = guids.get(1);
        String extension = parseFileExtension(loadDocumentRequestFirst.getGuid());
        // check if files extensions are the same and support format file
        return extension.equals(parseFileExtension(loadDocumentRequestSecond.getGuid())) && checkSupportedFiles(extension.toLowerCase());
    }

    @Override
    public LoadDocumentEntity loadDocument(LoadDocumentRequest loadDocumentRequest) {
        try {
            LoadDocumentEntity loadDocumentEntity = new LoadDocumentEntity();
            loadDocumentEntity.setGuid(loadDocumentRequest.getGuid());
            List<PageDescriptionEntity> pageDescriptionEntities = loadPages(loadDocumentRequest.getGuid(), loadDocumentRequest.getPassword());
            loadDocumentEntity.setPages(pageDescriptionEntities);
            return loadDocumentEntity;
        } catch (Exception ex) {
            throw new TotalGroupDocsException(getExceptionMessage(loadDocumentRequest.getPassword(), ex), ex);
        }
    }

    private List<PageDescriptionEntity> loadPages(String guid, String password) {
        Comparer comparer = new Comparer();
        List<PageImage> pageImages = comparer.convertToImages(guid, password);
        try {
            List<PageDescriptionEntity> pageDescriptionEntities = getPageDescriptionEntities(pageImages);
            return pageDescriptionEntities;
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new TotalGroupDocsException(e.getMessage());
        }
    }

    private List<PageDescriptionEntity> getPageDescriptionEntities(List<PageImage> containerPages) throws IOException {
        List<PageDescriptionEntity> pages = new ArrayList<>();
        for (int i = 0; i < containerPages.size(); i++) {
            PageImage page = containerPages.get(i);
            PageDescriptionEntity pageDescriptionEntity = getPageDescriptionEntity(page);
            pages.add(pageDescriptionEntity);
        }
        return pages;
    }

    private PageDescriptionEntity getPageDescriptionEntity(PageImage page) throws IOException {
        PageDescriptionEntity pageDescriptionEntity = new PageDescriptionEntity();
        pageDescriptionEntity.setNumber(page.getPageNumber());
        pageDescriptionEntity.setHeight(page.getHeight());
        pageDescriptionEntity.setWidth(page.getWidth());
        pageDescriptionEntity.setData(getStringFromStream(page.getPageStream()));
        return pageDescriptionEntity;
    }

    /**
     * Replace empty string with null
     *
     * @param password
     * @return password or null if password is empty
     */
    private String convertEmptyPasswordToNull(String password) {
        return StringUtils.isEmpty(password) ? null : password;
    }

    /**
     * Check support formats for comparing
     *
     * @param extension file extension
     * @return true - format is supported, false - format is not supported
     */
    private boolean checkSupportedFiles(String extension) {
        switch (extension) {
            case DOC:
            case DOCX:
            case XLS:
            case XLSX:
            case PPT:
            case PPTX:
            case PDF:
            case TXT:
            case HTML:
            case HTM:
                return true;
            default:
                return false;
        }
    }

    private ICompareResult compareFiles(LoadDocumentRequest loadDocumentRequestFirst, LoadDocumentRequest loadDocumentRequestSecond) {
        Comparer comparer = new Comparer();
        ComparisonSettings settings = new ComparisonSettings();
        settings.setShowDeletedContent(false);
        settings.setStyleChangeDetection(true);
        settings.setCalculateComponentCoordinates(true);
        ICompareResult compareResult = comparer.compare(loadDocumentRequestSecond.getGuid(),
                convertEmptyPasswordToNull(loadDocumentRequestSecond.getPassword()),
                loadDocumentRequestFirst.getGuid(),
                convertEmptyPasswordToNull(loadDocumentRequestFirst.getPassword()),
                settings);
        if (compareResult == null) {
            throw new TotalGroupDocsException("Something went wrong. We've got null result.");
        }
        return compareResult;
    }

    /**
     * Save file
     *
     * @param guid        unique key of results
     * @param inputStream stream for saving
     * @param ext         result file extension
     * @return path to saved file
     */
    private String saveFile(String guid, InputStream inputStream, String ext) {
        String imageFileName = calculateResultFileName(guid, ext);
        try {
            Files.copy(inputStream, Paths.get(imageFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Exception occurred while write result images files.");
        }
        return imageFileName;
    }

    public String calculateResultFileName(String documentGuid, String ext) {
        // configure file name for results
        String resultDirectory = getResultDirectory();
        String extension = ext != null ? getRightExt(ext.toLowerCase()) : "";
        return String.format("%s%s%s.%s", resultDirectory, File.separator, documentGuid, extension);
    }

    /**
     * Fix file extensions for some formats
     *
     * @param ext extension string
     * @return right extension for result file
     */
    private String getRightExt(String ext) {
        switch (ext) {
            case DOC:
            case DOCX:
                return DOCX;
            case XLS:
            case XLSX:
                return XLSX;
            case PPT:
            case PPTX:
                return PPTX;
            default:
                return ext;
        }
    }

    private String getResultDirectory() {
        return StringUtils.isEmpty(comparisonConfiguration.getResultDirectory()) ? comparisonConfiguration.getFilesDirectory() : comparisonConfiguration.getResultDirectory();
    }

}

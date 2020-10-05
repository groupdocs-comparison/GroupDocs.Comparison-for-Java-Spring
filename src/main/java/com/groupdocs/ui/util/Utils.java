package com.groupdocs.ui.util;

import com.groupdocs.ui.config.ServerConfiguration;
import com.groupdocs.ui.exception.TotalGroupDocsException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * Set local port from request to config
     */
    public static void setLocalPort(HttpServletRequest request, ServerConfiguration server) {
        if (server.getHttpPort() == null) {
            server.setHttpPort(request.getLocalPort());
        }
    }

    /**
     * Parse extension of the file's name
     *
     * @param documentGuid path to file
     * @return extension of the file's name
     */
    public static String parseFileExtension(String documentGuid) {
        String extension = FilenameUtils.getExtension(documentGuid);
        return extension == null ? null : extension.toLowerCase();
    }

    /**
     * Fill header HTTP response with file data
     */
    public static void addFileDownloadHeaders(HttpServletResponse response, String fileName, Long fileLength) {
        HttpHeaders fileDownloadHeaders = createFileDownloadHeaders(fileName, fileLength, MediaType.APPLICATION_OCTET_STREAM);
        for (Map.Entry<String, List<String>> entry : fileDownloadHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                response.addHeader(entry.getKey(), value);
            }
        }
    }

    /**
     * Upload the file
     *
     * @param documentStoragePath path for uploading the file
     * @param content             file data
     * @param url                 url of file
     * @param rewrite             flag of rewriting the file
     * @return path to uploaded file
     */
    public static String uploadFile(String documentStoragePath, MultipartFile content, String url, Boolean rewrite) {
        String filePath;
        try {
            String fileName;
            // save from file content
            if (StringUtils.isEmpty(url)) {
                fileName = content.getOriginalFilename();
                try (InputStream inputStream = content.getInputStream()) {
                    filePath = uploadFileInternal(inputStream, documentStoragePath, fileName, rewrite);
                } catch (Exception ex) {
                    logger.error("Exception occurred while uploading document", ex);
                    throw new TotalGroupDocsException(ex.getMessage(), ex);
                }
            } else { // save from url
                URL fileUrl = new URL(url);
                try (InputStream inputStream = fileUrl.openStream()) {
                    fileName = FilenameUtils.getName(fileUrl.getPath());
                    filePath = uploadFileInternal(inputStream, documentStoragePath, fileName, rewrite);
                } catch (Exception ex) {
                    logger.error("Exception occurred while uploading document", ex);
                    throw new TotalGroupDocsException(ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            logger.error("Exception occurred while uploading document", ex);
            throw new TotalGroupDocsException(ex.getMessage(), ex);
        }
        return filePath;
    }

    /**
     * Upload file from input stream
     *
     * @param uploadedInputStream input stream of file
     * @param documentStoragePath path to storage
     * @param fileName            name of file
     * @param rewrite             flag for rewriting
     * @return path to file
     */
    public static String uploadFileInternal(InputStream uploadedInputStream, String documentStoragePath, String fileName, boolean rewrite) throws IOException {
        String filePath = String.format("%s%s%s", documentStoragePath, File.separator, fileName);
        File file = new File(filePath);
        // check rewrite mode
        if (rewrite) {
            // save file with rewrite if exists
            Files.copy(uploadedInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return filePath;
        } else {
            if (file.exists()) {
                // get file with new name
                file = getFreeFileName(documentStoragePath, fileName);
            }
            // save file without rewriting
            Path path = file.toPath();
            Files.copy(uploadedInputStream, path);
            return path.toString();
        }
    }

    /**
     * Get headers for downloading files
     */
    private static HttpHeaders createFileDownloadHeaders(String fileName, Long fileLength, MediaType mediaType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentDispositionFormData("attachment", fileName);
        httpHeaders.setContentType(mediaType);
        httpHeaders.set("Content-Description", "File Transfer");
        httpHeaders.set("Content-Transfer-Encoding", "binary");
        httpHeaders.setExpires(0);
        httpHeaders.setCacheControl("must-revalidate");
        httpHeaders.setPragma("public");
        if (fileLength != null) {
            httpHeaders.setContentLength(fileLength);
        }
        return httpHeaders;
    }

    /**
     * Rename file if exist
     *
     * @param directory directory where files are located
     * @param fileName  file name
     * @return new file with new file name
     */
    public static File getFreeFileName(String directory, String fileName) {
        File file = null;
        try {
            File folder = new File(directory);
            File[] listOfFiles = folder.listFiles();
            assert listOfFiles != null : "2808aff2-ecc3-42e4-9f22-47533c44d014";
            for (int i = 0; i < listOfFiles.length; i++) {
                int number = i + 1;
                String newFileName = FilenameUtils.removeExtension(fileName) + "-Copy(" + number + ")." + FilenameUtils.getExtension(fileName);
                file = new File(directory + File.separator + newFileName);
                if (!file.exists()) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }
}

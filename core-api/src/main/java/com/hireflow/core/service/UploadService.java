package com.hireflow.core.service;

import com.hireflow.core.service.CandidateTx.PendingUpload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadService {

    static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final CandidateTx store;
    private final PipelineService pipeline;

    public UploadService(CandidateTx store, PipelineService pipeline) {
        this.store = store;
        this.pipeline = pipeline;
    }

    public List<UUID> accept(UUID jobId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at least one PDF");
        }
        List<PendingUpload> uploads = new ArrayList<>();
        for (MultipartFile file : files) {
            uploads.add(read(file));
        }
        List<UUID> ids = store.insertPending(jobId, uploads);
        UploadBatch batch = new UploadBatch(jobId, new LinkedHashSet<>(ids));
        for (int index = 0; index < ids.size(); index++) {
            PendingUpload upload = uploads.get(index);
            pipeline.processCandidate(ids.get(index), upload.filename(), upload.bytes(), batch);
        }
        return ids;
    }

    private static PendingUpload read(MultipartFile file) {
        String filename = filename(file);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        if (bytes.length > MAX_PDF_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF exceeds the 10MB size limit");
        }
        if (!hasPdfHeader(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }
        return new PendingUpload(filename, bytes);
    }

    private static boolean hasPdfHeader(byte[] bytes) {
        return bytes.length >= PDF_HEADER.length
                && Arrays.equals(bytes, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length);
    }

    private static String filename(MultipartFile file) {
        String raw = file.getOriginalFilename();
        String name = raw == null ? "" : raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        return name.length() <= 512 ? name : name.substring(0, 512);
   
    }
}

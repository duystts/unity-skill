package com.unityskill.attachment;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;
    @Value("${cloudinary.api-key}")
    private String apiKey;
    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    void init() {
        cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key",    apiKey,
            "api_secret", apiSecret,
            "secure",     true
        ));
    }

    /** Upload and return the Cloudinary response map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> upload(MultipartFile file, String folder) throws IOException {
        return cloudinary.uploader().upload(
            file.getBytes(),
            ObjectUtils.asMap(
                "folder",          folder,
                "resource_type",   "auto",   // detects image vs video
                "use_filename",    true,
                "unique_filename", true
            )
        );
    }

    /** Delete a resource from Cloudinary by its public_id. */
    public void delete(String publicId, String resourceType) throws IOException {
        cloudinary.uploader().destroy(publicId,
            ObjectUtils.asMap("resource_type", resourceType));
    }
}

package com.unityskill.consent;

import com.unityskill.consent.entity.ConsentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;

    @Transactional(readOnly = true)
    public boolean hasConsented(UUID userId) {
        return consentRepository.existsByUserId(userId);
    }

    /**
     * AC3: Idempotent — returns existing record if user already consented.
     * Creates a new ConsentRecord with consentedAt = now() on first call.
     */
    @Transactional
    public ConsentRecord recordConsent(UUID userId) {
        return consentRepository.findByUserId(userId)
                .orElseGet(() -> consentRepository.save(
                        ConsentRecord.builder()
                                .userId(userId)
                                .consentedAt(Instant.now())
                                .build()  // consentVersion defaults to "1.0" via @Builder.Default
                ));
    }
}

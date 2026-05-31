package com.unityskill.consent;

import com.unityskill.consent.entity.ConsentRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock ConsentRepository consentRepository;
    @InjectMocks ConsentService consentService;

    @Test
    void hasConsented_existingRecord_returnsTrue() {
        UUID userId = UUID.randomUUID();
        when(consentRepository.existsByUserId(userId)).thenReturn(true);

        assertThat(consentService.hasConsented(userId)).isTrue();
    }

    @Test
    void hasConsented_noRecord_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(consentRepository.existsByUserId(userId)).thenReturn(false);

        assertThat(consentService.hasConsented(userId)).isFalse();
    }

    @Test
    void recordConsent_newUser_savesRecordWithTimestamp() {
        // AC3: ConsentRecord created with consentedAt timestamp
        UUID userId = UUID.randomUUID();
        when(consentRepository.findByUserId(userId)).thenReturn(Optional.empty());
        ConsentRecord saved = ConsentRecord.builder()
                .id(UUID.randomUUID()).userId(userId)
                .consentedAt(Instant.now()).consentVersion("1.0").build();
        when(consentRepository.save(any())).thenReturn(saved);

        ConsentRecord result = consentService.recordConsent(userId);

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consentRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getConsentedAt()).isNotNull();
        assertThat(captor.getValue().getConsentVersion()).isEqualTo("1.0");
        assertThat(result).isEqualTo(saved);
    }
}

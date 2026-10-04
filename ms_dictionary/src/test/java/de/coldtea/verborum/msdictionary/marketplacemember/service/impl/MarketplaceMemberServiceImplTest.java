package de.coldtea.verborum.msdictionary.marketplacemember.service.impl;

import de.coldtea.verborum.msdictionary.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.marketplacemember.entity.MarketplaceMember;
import de.coldtea.verborum.msdictionary.marketplacemember.repository.MarketplaceMemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MarketplaceMemberServiceImplTest {

    private static final String KEYCLOAK_ID = "kc-1";

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 10, 4, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = T1.plusMinutes(5);

    @Mock
    private MarketplaceMemberRepository marketplaceMemberRepository;

    @Mock
    private DictionaryService dictionaryService;

    @InjectMocks
    private MarketplaceMemberServiceImpl marketplaceMemberService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void applyProfileUpdate_FirstJoin_SharesAllDictionaries() {
        // Arrange — never seen before = not a member
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());

        // Act
        marketplaceMemberService.applyProfileUpdate(event(true, T1));

        // Assert
        MarketplaceMember saved = capturedSave();
        assertTrue(saved.getIsMember());
        assertEquals(T1, saved.getSourceUpdatedAt());
        verify(dictionaryService).setVisibilityOfAll(KEYCLOAK_ID, true);
    }

    @Test
    void applyProfileUpdate_Leave_MakesAllPrivateAfterRecordingTheLeave() {
        // Arrange — the leave must be saved first, or the sharing rule would refuse to hide the last one
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(member(true, T1)));

        // Act
        marketplaceMemberService.applyProfileUpdate(event(false, T2));

        // Assert
        InOrder inOrder = inOrder(marketplaceMemberRepository, dictionaryService);
        inOrder.verify(marketplaceMemberRepository).saveAndFlush(any());
        inOrder.verify(dictionaryService).setVisibilityOfAll(KEYCLOAK_ID, false);
    }

    @Test
    void applyProfileUpdate_RejoinAfterLeaving_SharesAllAgain() {
        // Arrange — every re-join shares everything, including dictionaries hidden earlier
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(member(false, T1)));

        // Act
        marketplaceMemberService.applyProfileUpdate(event(true, T2));

        // Assert
        verify(dictionaryService).setVisibilityOfAll(KEYCLOAK_ID, true);
    }

    @Test
    void applyProfileUpdate_StillAMember_SharesNothing() {
        // Arrange — e.g. a rename: no transition, so dictionaries the member hid stay hidden
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(member(true, T1)));

        // Act
        marketplaceMemberService.applyProfileUpdate(event(true, T2));

        // Assert
        verify(dictionaryService, never()).setVisibilityOfAll(anyString(), anyBoolean());
    }

    @Test
    void applyProfileUpdate_NeverJoinedAndStillNot_SharesNothing() {
        // Arrange — a profile created without accepting
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());

        // Act
        marketplaceMemberService.applyProfileUpdate(event(false, T1));

        // Assert
        assertFalse(capturedSave().getIsMember());
        verify(dictionaryService, never()).setVisibilityOfAll(anyString(), anyBoolean());
    }

    @Test
    void applyProfileUpdate_RedeliveredJoin_RunsNothingTwice() {
        // Arrange — equal counts as stale (rule 4)
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(member(true, T1)));

        // Act
        marketplaceMemberService.applyProfileUpdate(event(true, T1));

        // Assert
        verify(marketplaceMemberRepository, never()).saveAndFlush(any());
        verify(dictionaryService, never()).setVisibilityOfAll(anyString(), anyBoolean());
    }

    @Test
    void applyProfileUpdate_OlderLeaveArrivingLate_IsDropped() {
        // Arrange — a leave overtaken by a re-join must not unshare everything
        when(marketplaceMemberRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(member(true, T2)));

        // Act
        marketplaceMemberService.applyProfileUpdate(event(false, T1));

        // Assert
        verify(marketplaceMemberRepository, never()).saveAndFlush(any());
        verify(dictionaryService, never()).setVisibilityOfAll(anyString(), anyBoolean());
    }

    @Test
    void applyProfileUpdate_EventWithoutTheFlag_IsIgnored() {
        // Act — a pre-P4-14 event knows nothing about membership
        marketplaceMemberService.applyProfileUpdate(event(null, T2));

        // Assert
        verifyNoInteractions(marketplaceMemberRepository, dictionaryService);
    }

    private MarketplaceMember capturedSave() {
        ArgumentCaptor<MarketplaceMember> captor = ArgumentCaptor.forClass(MarketplaceMember.class);
        verify(marketplaceMemberRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static UserProfileUpdatedEvent event(Boolean accepted, OffsetDateTime updatedAt) {
        return UserProfileUpdatedEvent.builder()
                .keycloakId(KEYCLOAK_ID)
                .displayName("Anna")
                .marketplaceAgreementAccepted(accepted)
                .updatedAt(updatedAt)
                .eventTimestamp(T2.plusMinutes(1))
                .build();
    }

    private static MarketplaceMember member(boolean isMember, OffsetDateTime sourceUpdatedAt) {
        return MarketplaceMember.builder().keycloakId(KEYCLOAK_ID).isMember(isMember).sourceUpdatedAt(sourceUpdatedAt).build();
    }
}

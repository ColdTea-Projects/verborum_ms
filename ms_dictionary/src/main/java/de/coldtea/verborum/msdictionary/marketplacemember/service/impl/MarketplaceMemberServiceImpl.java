package de.coldtea.verborum.msdictionary.marketplacemember.service.impl;

import de.coldtea.verborum.msdictionary.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.marketplacemember.entity.MarketplaceMember;
import de.coldtea.verborum.msdictionary.marketplacemember.repository.MarketplaceMemberRepository;
import de.coldtea.verborum.msdictionary.marketplacemember.service.MarketplaceMemberService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceMemberServiceImpl implements MarketplaceMemberService {

    private final MarketplaceMemberRepository marketplaceMemberRepository;

    private final DictionaryService dictionaryService;

    /**
     * One transaction: the membership row and every visibility change commit together, and the
     * visibility events go out after commit (rule 1). The transition is what matters, not the state:
     * an event saying "member" for someone already held as a member shares nothing, so a member who
     * hid some dictionaries does not get them re-shared by an unrelated profile change.
     */
    @Transactional
    @Override
    public void applyProfileUpdate(UserProfileUpdatedEvent event) {
        if (event.getMarketplaceAgreementAccepted() == null) {
            return;
        }

        OffsetDateTime sourceUpdatedAt = Objects.requireNonNullElse(event.getUpdatedAt(), event.getEventTimestamp());
        Optional<MarketplaceMember> held = marketplaceMemberRepository.findById(event.getKeycloakId());

        // Equal counts as stale (rule 4): a redelivered join must not run the transition twice
        if (held.isPresent() && !sourceUpdatedAt.isAfter(held.get().getSourceUpdatedAt())) {
            return;
        }

        boolean wasMember = held.map(member -> Boolean.TRUE.equals(member.getIsMember())).orElse(false);
        boolean isMember = event.getMarketplaceAgreementAccepted();

        MarketplaceMember member = held.orElseGet(() -> MarketplaceMember.builder()
                .keycloakId(event.getKeycloakId())
                .build());
        member.setIsMember(isMember);
        member.setSourceUpdatedAt(sourceUpdatedAt);
        // Saved before the visibility change: making everything private must already see a non-member,
        // or the sharing rule would refuse to hide the last shared dictionary
        marketplaceMemberRepository.saveAndFlush(member);

        if (isMember != wasMember) {
            int changed = dictionaryService.setVisibilityOfAll(event.getKeycloakId(), isMember);
            log.info("Marketplace {} for keycloakId {}: {} dictionaries made {}",
                    isMember ? "join" : "leave", event.getKeycloakId(), changed, isMember ? "public" : "private");
        }
    }
}

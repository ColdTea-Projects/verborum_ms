package de.coldtea.verborum.msdictionary.marketplacemember.repository;

import de.coldtea.verborum.msdictionary.marketplacemember.entity.MarketplaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceMemberRepository extends JpaRepository<MarketplaceMember, String> {

    // The sharing rule (P4-16): only members must keep a dictionary shared
    boolean existsByKeycloakIdAndIsMemberTrue(String keycloakId);
}

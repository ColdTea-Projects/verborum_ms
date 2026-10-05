package de.coldtea.verborum.msuser.common.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayNameUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // reserved anywhere, including inside a longer word
            "Verborum", "Verborum Team", "TheVerborumGuy", "ColdTea", "Admin", "xAdminx", "Site Administrator",
            "Moderator Anna", "Official Dictionaries",
            // disguised: case, accents, look-alikes, separators
            "VERBORUM", "Vérbörum", "V e r b o r u m", "v.e.r.b.o.r.u.m", "4dm1n", "@dmin", "0fficial", "admin_42",
            // reserved only as whole words
            "Support", "Anna Support", "Staff", "Team Verbs", "System", "Mod", "root", "Security", "Help", "Helpdesk",
            "Bot", "$upport", "t3am",
            // Turkish default locale must not matter: "I" lowercases to "i" under Locale.ROOT
            "ADMIN", "OFFICIAL"
    })
    void isReserved_ReservedNames_ReturnTrue(String name) {
        assertTrue(DisplayNameUtils.isReserved(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Anna Bauer", "Mehmet Yılmaz", "Léa Dubois", "Ahmet Çelik", "Steam Lover", "Modern Greek Fan",
            "Rooted in Words", "Helpful Hannah", "Robot Wars", "Teamwork", "Supporter of Languages", "Systematic Sam",
            "Botanist", "Moderna", "Officer Max"
    })
    void isReserved_OrdinaryNames_ReturnFalse(String name) {
        assertFalse(DisplayNameUtils.isReserved(name), name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void isReserved_NoName_ReturnsFalse(String name) {
        assertFalse(DisplayNameUtils.isReserved(name));
    }
}

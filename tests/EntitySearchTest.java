package fr.maison.tvlauncher;

public final class EntitySearchTest {
    private static int checks;
    public static void main(String[] args) {
        check("Lumière séjour light.sejour", "lumiere", true);
        check("Lumière séjour light.sejour", "SEJOUR", true);
        check("Lumière séjour light.sejour", "light.sejour", true);
        check("Lumière séjour light.sejour", "sejour lumiere", true);
        check("Éclairage entrée light.entree_principale", "entree principale", true);
        check("Éclairage entrée light.entree_principale", "light.entree_principale", true);
        check("Scène soirée cinéma scene.soiree_cinema", "cinema scene", true);
        check("Prise salon switch.prise_salon", "light", false);
        check("Prise salon switch.prise_salon", "salon cuisine", false);
        check("Prise salon switch.prise_salon", "  ", true);
        check("Prise salon switch.prise_salon", null, true);
        check(null, "salon", false);
        for (boolean selectedOnly : new boolean[]{false, true}) {
            for (boolean selected : new boolean[]{false, true}) {
                visibility("salon", selectedOnly, selected, !selectedOnly || selected);
                visibility("cuisine", selectedOnly, selected, false);
                visibility("", selectedOnly, selected, !selectedOnly || selected);
            }
        }
        System.out.println("PASS: " + checks + " entity-search checks");
    }
    private static void check(String content, String query, boolean expected) {
        checks++;
        if (EntitySearch.matches(EntitySearch.normalize(content), EntitySearch.normalize(query)) != expected)
            throw new AssertionError("Incorrect search: " + query);
    }
    private static void visibility(String query, boolean selectedOnly, boolean selected, boolean expected) {
        checks++;
        boolean actual = EntitySearch.visible(EntitySearch.normalize("Lumière salon light.salon"),
                EntitySearch.normalize(query), selectedOnly, selected);
        if (actual != expected) throw new AssertionError("Incorrect selection filter: " + query);
    }
}

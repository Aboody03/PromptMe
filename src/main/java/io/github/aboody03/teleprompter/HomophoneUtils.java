package io.github.aboody03.teleprompter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HomophoneUtils {
    private static final Map<String, Set<String>> HOMOPHONES = loadHomophones();

    private static Map<String, Set<String>> loadHomophones() {
        Map<String, Set<String>> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                HomophoneUtils.class.getResourceAsStream("/homophones.txt"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] group = line.split(",");
                for (String w : group) {
                    String key = w.trim().toLowerCase();
                    map.computeIfAbsent(key, k -> new HashSet<>());
                    for (String sib : group) {
                        sib = sib.trim().toLowerCase();
                        if (!sib.equals(key)) {
                            map.get(key).add(sib);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load homophones.txt");
            e.printStackTrace();
        }
        return map;
    }

    public static boolean wordsMatch(String heard, String expect) {
        if (heard.equals(expect)) return true;
        Set<String> alts = HOMOPHONES.get(heard);
        if (alts != null && alts.contains(expect)) return true;
        return levenshtein(heard, expect) <= 1;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m+1], curr = new int[m+1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j-1] + 1), prev[j-1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, m+1);
        }
        return prev[m];
    }
}

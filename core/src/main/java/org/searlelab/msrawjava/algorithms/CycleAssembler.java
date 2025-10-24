package org.searlelab.msrawjava.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;

public class CycleAssembler {
    private final List<Range> windowOrder;     // fixed set of windows defining a cycle
    private final Set<Range> windowSet;        // for membership checks
    private final Map<Range, FragmentScan> current = new LinkedHashMap<>();
    private final ArrayDeque<ArrayList<FragmentScan>> completed = new ArrayDeque<>();

    public CycleAssembler(Collection<Range> windows) {
        // Preserve a deterministic window order for stable cycle lists
        this.windowOrder = new ArrayList<>(windows);
        this.windowSet = new HashSet<>(windows);
    }

    public void add(FragmentScan fs) {
        final Range r = fs.getPrecursorRange();
        if (!windowSet.contains(r)) {
            // Unknown window: either ignore or treat as cycle boundary. We'll ignore.
            return;
        }

        if (current.containsKey(r)) {
            // We saw this window already in the current cycle → that means a new cycle began.
            // Finalize the current one if it has any content.
            finalizeCurrentIfNonEmpty();
            current.clear();
        }

        // Record this window for the current cycle.
        // If duplicate windows actually can occur *within* a cycle and you want "first wins",
        // guard with current.putIfAbsent(r, fs); For strictness, we overwrite as above.
        current.put(r, fs);

        // If we've collected all windows for a cycle, finalize it.
        if (current.size() == windowSet.size()) {
            finalizeCurrentIfNonEmpty();
            current.clear();
        }
    }

    private void finalizeCurrentIfNonEmpty() {
        if (current.isEmpty()) return;
        ArrayList<FragmentScan> cycle = new ArrayList<>(windowOrder.size());
        for (Range w : windowOrder) {
            FragmentScan fs = current.get(w);
            if (fs != null) cycle.add(fs);
            // If missing, you can decide to skip or allow partial cycles; here we require present windows only.
        }
        // Only accept cycles that have at least half the windows to be robust to rare glitches.
        if (!cycle.isEmpty() && cycle.size() >= Math.max(1, windowOrder.size() / 2)) {
            completed.addLast(cycle);
        }
    }

    /** Pull any completed cycles since last call. */
    public ArrayList<ArrayList<FragmentScan>> drainCompleted() {
        ArrayList<ArrayList<FragmentScan>> out = new ArrayList<>(completed);
        completed.clear();
        return out;
    }

    /** Call at end-of-file to flush any trailing partial cycle. */
    public void flushPartial() {
        finalizeCurrentIfNonEmpty();
        current.clear();
    }
}

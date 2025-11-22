package de.terranova.terraRenderer.refactor;

import de.terranova.terraRenderer.refactor.BlockDisplayNode;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Context passed to BlockDisplayNode click callbacks.
 * Provides access to the clicking player and the node itself.
 */
public class ClickContext {

    private final Player player;
    private final BlockDisplayNode node;

    public ClickContext(Player player, BlockDisplayNode node) {
        this.player = player;
        this.node = node;
    }

    /** The player who triggered the interaction. */
    public Player player() {
        return player;
    }

    /** The BlockDisplayNode that was interacted with. */
    public BlockDisplayNode node() {
        return node;
    }

    /**
     * Convenience helper: a singleton list containing only the interacting player.
     * Useful when calling node.update(...) or node.spawn(...) for just this viewer.
     */
    public List<Player> onlyViewer() {
        return Collections.singletonList(player);
    }
}

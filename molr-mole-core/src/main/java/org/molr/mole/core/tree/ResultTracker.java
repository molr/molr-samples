package org.molr.mole.core.tree;

import org.molr.commons.domain.Block;
import org.molr.commons.domain.Result;

public interface ResultTracker {

    Result resultFor(Block block);
}

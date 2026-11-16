package io.github.f4iyy.ubc780xlt.model.edit;

/** One undoable change. */
public interface Edit {

    String name();

    void undo();

    void redo();
}

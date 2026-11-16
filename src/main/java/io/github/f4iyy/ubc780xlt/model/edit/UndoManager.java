package io.github.f4iyy.ubc780xlt.model.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/** Undo/redo history of the edits made in the tables. */
public final class UndoManager {

    private static final int LIMIT = 200;

    private final Deque<Edit> undoStack = new ArrayDeque<>();
    private final Deque<Edit> redoStack = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(false);

    /** Remembers an edit that has just been applied; this clears the redo history. */
    public void record(Edit edit) {
        undoStack.push(edit);
        if (undoStack.size() > LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
        refresh();
    }

    public void undo() {
        Edit edit = undoStack.poll();
        if (edit != null) {
            edit.undo();
            redoStack.push(edit);
            refresh();
        }
    }

    public void redo() {
        Edit edit = redoStack.poll();
        if (edit != null) {
            edit.redo();
            undoStack.push(edit);
            refresh();
        }
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        refresh();
    }

    public ReadOnlyBooleanProperty canUndo() {
        return canUndo.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canRedo() {
        return canRedo.getReadOnlyProperty();
    }

    /** Name of the edit {@link #undo()} would revert, or an empty string. */
    public String undoName() {
        return undoStack.isEmpty() ? "" : undoStack.peek().name();
    }

    private void refresh() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }
}

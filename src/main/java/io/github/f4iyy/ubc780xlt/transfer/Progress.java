package io.github.f4iyy.ubc780xlt.transfer;

/** Told how far a transfer has got. */
@FunctionalInterface
public interface Progress {

    Progress NONE = (task, done, total) -> { };

    /**
     * @param task what is being done, e.g. {@code Channel 42}
     * @param done steps finished
     * @param total steps in all
     */
    void update(String task, int done, int total);
}

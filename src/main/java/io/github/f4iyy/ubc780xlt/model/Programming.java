package io.github.f4iyy.ubc780xlt.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The whole scanner programming: 10 scan banks, 10 search banks and 500 channels (10 banks of 50).
 * The sizes are fixed; "deleting" a channel empties it.
 */
public final class Programming {

    public static final int BANKS = 10;
    public static final int CHANNELS_PER_BANK = 50;
    public static final int CHANNELS = BANKS * CHANNELS_PER_BANK;

    private final List<ScanBank> scanBanks;
    private final List<SearchBank> searchBanks;
    private final List<Channel> channels;

    public Programming() {
        scanBanks = IntStream.rangeClosed(1, BANKS).mapToObj(ScanBank::new).toList();
        searchBanks = IntStream.rangeClosed(1, BANKS).mapToObj(SearchBank::new).toList();
        channels = IntStream.rangeClosed(1, CHANNELS).mapToObj(Channel::new).toList();
        resetPriorities();
        markClean();
    }

    public List<ScanBank> scanBanks() {
        return scanBanks;
    }

    public List<SearchBank> searchBanks() {
        return searchBanks;
    }

    public List<Channel> channels() {
        return channels;
    }

    /** @param number 1 to 500 */
    public Channel channel(int number) {
        return channels.get(number - 1);
    }

    /** The 50 channels of a bank. @param bank 1 to 10 */
    public List<Channel> bankChannels(int bank) {
        int from = (bank - 1) * CHANNELS_PER_BANK;
        return channels.subList(from, from + CHANNELS_PER_BANK);
    }

    /** A copy of the content of the 500 channels. */
    public List<ChannelData> snapshot() {
        List<ChannelData> out = new ArrayList<>(CHANNELS);
        for (Channel c : channels) {
            out.add(c.data());
        }
        return out;
    }

    /** Writes the content of {@code first.. first+data.size()-1} (index 0 based). */
    public void apply(int first, List<ChannelData> data) {
        for (int i = 0; i < data.size(); i++) {
            channels.get(first + i).set(data.get(i));
        }
    }

    /** True if anything differs from the last read, write or save. */
    public boolean isModified() {
        return channels.stream().anyMatch(c -> c.modified().get())
                || scanBanks.stream().anyMatch(b -> b.modified().get())
                || searchBanks.stream().anyMatch(b -> b.modified().get());
    }

    public void markClean() {
        channels.forEach(Channel::markClean);
        scanBanks.forEach(ScanBank::markClean);
        searchBanks.forEach(SearchBank::markClean);
    }

    /** Empties everything and marks it clean. */
    public void clear() {
        channels.forEach(c -> c.set(ChannelData.empty()));
        resetPriorities();
        scanBanks.forEach(b -> b.set(new ScanBankData("")));
        searchBanks.forEach(b -> b.set(SearchBankData.empty()));
        markClean();
    }

    /** Like a factory-fresh scanner: the first channel of each bank is its priority channel. */
    private void resetPriorities() {
        for (int bank = 1; bank <= BANKS; bank++) {
            Channel first = bankChannels(bank).get(0);
            first.set(first.data().withPriority(true));
        }
    }

    /** Copies the whole content of {@code other} (used when a file is opened). */
    public void replaceWith(Programming other) {
        for (int i = 0; i < CHANNELS; i++) {
            channels.get(i).set(other.channels.get(i).data());
        }
        for (int i = 0; i < BANKS; i++) {
            scanBanks.get(i).set(other.scanBanks.get(i).data());
            searchBanks.get(i).set(other.searchBanks.get(i).data());
        }
        markClean();
    }
}

package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.Response;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.Backlight;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.CtcssFunction;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.MuteMode;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsViewModelTest {

    private FakeScanner scanner;
    private ConnectionManager manager;
    private Messages messages;
    private SettingsViewModel vm;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        messages = Messages.of("en");
        vm = new SettingsViewModel(manager, messages, Runnable::run, Runnable::run);
        manager.connect("P", 0); // the view model loads the settings when the link comes up
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void settingsAreLoadedWhenTheLinkComesUp() {
        assertTrue(vm.connected().get());
        assertEquals(Backlight.NORMAL, vm.backlight().get());
        assertEquals(MuteMode.AUTO, vm.mute().get());
        assertTrue(vm.dataSkip().get());
        assertTrue(vm.scanBank(10).get());
        assertTrue(vm.searchBank(1).get());
        assertFalse(vm.searchBank(2).get());
        assertEquals(messages.get("settings.loaded"), vm.message().get());
    }

    @Test
    void aChangeIsWrittenAtOnce() {
        vm.beep().set(true);
        vm.backlight().set(Backlight.OFF);
        vm.mute().set(MuteMode.OPEN);
        vm.ctcss().set(CtcssFunction.ON);
        vm.priority().set(true);
        vm.dataSkip().set(false);
        vm.editLock().set(true);
        vm.screenMask().set(true);

        List<String> sent = scanner.received();
        for (String c : List.of("BPN", "LTF", "MUN", "CTN", "PRN", "DSF", "ELN", "LMN")) {
            assertTrue(sent.contains(c), c + " in " + sent);
        }
        assertEquals(messages.get("settings.saved", messages.get("settings.screenMask")), vm.message().get());
    }

    @Test
    void banksAreWrittenAsLetters() {
        vm.scanBank(2).set(false);
        assertTrue(scanner.received().contains("SB ACDEFGHIJ"));
        vm.searchBank(3).set(true);
        assertTrue(scanner.received().contains("CB AC"));
    }

    @Test
    void theLastBankCannotBeUnselected() {
        vm.searchBank(1).set(false);
        assertTrue(vm.searchBank(1).get(), "put back");
        assertEquals(messages.get("settings.oneBank"), vm.message().get());
        assertFalse(scanner.received().contains("CB "));
    }

    @Test
    void aRefusedChangeIsUndone() throws IOException {
        // A scanner that says NG (not allowed in this mode) to every write.
        CommandSender refusing = command -> command.length() == 2
                ? manager.send(command)
                : new Response(Response.Kind.NG, "NG");
        SettingsViewModel v = new SettingsViewModel(refusing, manager, messages, Runnable::run, Runnable::run);
        v.load();
        assertFalse(v.beep().get());

        v.beep().set(true);

        assertFalse(v.beep().get(), "back to what the scanner has");
        assertEquals(messages.get("settings.notNow", messages.get("settings.beep")), v.message().get());
    }

    @Test
    void anErrorAnswerIsUndoneToo() {
        CommandSender erring = command -> command.length() == 2
                ? safe(command)
                : new Response(Response.Kind.ERR, "ERR");
        SettingsViewModel v = new SettingsViewModel(erring, manager, messages, Runnable::run, Runnable::run);
        v.load();
        v.backlight().set(Backlight.DIM);
        assertEquals(Backlight.NORMAL, v.backlight().get());
        assertEquals(messages.get("settings.refused", messages.get("settings.backlight")), v.message().get());
    }

    private Response safe(String command) throws IOException {
        return manager.send(command);
    }

    @Test
    void aBrokenLinkIsUndoneAndReported() {
        scanner.unplug();
        vm.beep().set(true);
        assertFalse(vm.beep().get());
        assertTrue(vm.message().get().startsWith("Settings error"), vm.message().get());
    }

    @Test
    void nothingIsWrittenWhenNotConnected() {
        manager.disconnect();
        assertFalse(vm.connected().get());
        int before = scanner.received().size();
        vm.beep().set(true);
        assertEquals(before, scanner.received().size());
    }

    @Test
    void theSkipMemoryGrowsAndRefusesBadFrequencies() {
        vm.addSkip("466,45");
        assertEquals(List.of("466.4500"), List.copyOf(vm.skipList()));
        assertEquals(messages.get("settings.skipAdded", "466.4500"), vm.message().get());

        vm.addSkip("466.45");
        assertEquals(1, vm.skipList().size(), "no duplicate");

        vm.addSkip("abc");
        assertEquals(messages.get("tune.invalid"), vm.message().get());
        vm.addSkip("700");
        assertEquals(messages.get("tune.invalid"), vm.message().get());
        assertEquals(1, vm.skipList().size());
    }

    @Test
    void aRefusedSkipFrequencyIsReported() {
        CommandSender refusing = command -> command.startsWith("SS") && command.length() == 10
                ? new Response(Response.Kind.NG, "NG")
                : safeQuiet(command);
        SettingsViewModel v = new SettingsViewModel(refusing, manager, messages, Runnable::run, Runnable::run);
        v.addSkip("145.5");
        assertEquals(messages.get("settings.refused", messages.get("settings.skip")), v.message().get());
        assertTrue(v.skipList().isEmpty());
    }

    private Response safeQuiet(String command) throws IOException {
        return manager.send(command);
    }

    @Test
    void theSkipMemoryHasACapacity() {
        for (int i = 0; i < 200; i++) {
            scanner.addSkip(1000000L + i);
        }
        vm.load();
        assertEquals(200, vm.skipList().size());
        vm.addSkip("145.5");
        assertEquals(messages.get("settings.skipFull"), vm.message().get());
    }

    @Test
    void readFailuresAreReported() {
        CommandSender dead = command -> {
            throw new IOException("Cable removed");
        };
        SettingsViewModel v = new SettingsViewModel(dead, manager, messages, Runnable::run, Runnable::run);
        v.load();
        assertTrue(v.message().get().startsWith("Settings error"));
    }

    @Test
    void warningsWhenTheScannerSaysSomethingUnexpected() {
        CommandSender odd = command -> new Response(Response.Kind.DATA, "???");
        SettingsViewModel v = new SettingsViewModel(odd, manager, messages, Runnable::run, Runnable::run);
        v.load();
        assertTrue(v.message().get().contains("not understood"), v.message().get());
    }
}

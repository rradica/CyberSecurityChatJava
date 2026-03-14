package com.secassist.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BugFlagsTest {

    @Test
    void defaultConstructorEnablesAllBugs() {
        var flags = new BugFlagsProperties();

        assertThat(flags.handoverScope()).isTrue();
        assertThat(flags.existenceOracle()).isTrue();
        assertThat(flags.threadStickiness()).isTrue();
        assertThat(flags.trustMerge()).isTrue();
        assertThat(flags.toolFasttrack()).isTrue();
    }

    @Test
    void canDisableIndividualBugs() {
        var flags = new BugFlagsProperties(false, true, false, true, false);

        assertThat(flags.handoverScope()).isFalse();
        assertThat(flags.existenceOracle()).isTrue();
        assertThat(flags.threadStickiness()).isFalse();
        assertThat(flags.trustMerge()).isTrue();
        assertThat(flags.toolFasttrack()).isFalse();
    }

    @Test
    void allDisabled() {
        var flags = new BugFlagsProperties(false, false, false, false, false);

        assertThat(flags.handoverScope()).isFalse();
        assertThat(flags.existenceOracle()).isFalse();
        assertThat(flags.threadStickiness()).isFalse();
        assertThat(flags.trustMerge()).isFalse();
        assertThat(flags.toolFasttrack()).isFalse();
    }
}

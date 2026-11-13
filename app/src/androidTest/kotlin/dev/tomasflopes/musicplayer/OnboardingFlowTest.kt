package dev.tomasflopes.musicplayer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First run, end to end: permission granted, the sample folder already chosen (the folder picker is a system
 * screen, so [FlowRig] saves the folder through the settings port, as picking it would), scan finished. Home
 * must then offer Shuffle all and Recently added. Needs the sample set on the device (scripts/push-samples.sh)
 * and runs with `./gradlew deviceCheck` on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    private val rig = FlowRig()

    @Before
    fun setUp() {
        rig.prepare()
    }

    @After
    fun tearDown() {
        rig.restore()
    }

    @Test
    fun aScannedSampleFolderShowsShuffleAllAndRecentlyAddedOnHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rig.screen.waitFor("Shuffle all")
            rig.screen.waitFor("Recently added")
        }
    }
}

package dev.tomasflopes.musicplayer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shuffle all from Home: within [PLAY_WAIT_MS] of the tap the mini-player must show the title of a song of the
 * sample set and the pause icon (so it is playing). Needs the sample set on the device and the emulator.
 */
@RunWith(AndroidJUnit4::class)
class ShuffleAllFlowTest {
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
    fun shuffleAllShowsASampleTitleAndThePauseIconWithinFiveSeconds() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val screen = rig.screen
            screen.click(screen.waitFor("Shuffle all"))

            screen.until("a sample title and the pause icon in the mini-player", PLAY_WAIT_MS) {
                val title = rig.sampleTitles.firstOrNull { screen.find(it) != null }
                title.takeIf { screen.find("Pause") != null }
            }
        }
    }
}

package dev.wolzard.recon.work

import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.work.ResultPresenter.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultPresenterTest {
    private fun decide(state: JobState = JobState.DONE, sheet: Boolean = false, app: Boolean = false, overlay: Boolean = true) =
        ResultPresenter.decide(state, sheet, app, overlay)

    @Test fun successWithNothingOnScreenPopsTheSheet() = assertEquals(Action.POP, decide())

    @Test fun failureOnlyNotifies() {
        assertEquals(Action.NOTIFY, decide(state = JobState.FAILED))
        assertEquals(Action.NOTIFY, decide(state = JobState.FAILED, overlay = true, app = false))
    }

    @Test fun withoutTheOverlayPermissionSuccessNotifies() = assertEquals(Action.NOTIFY, decide(overlay = false))

    @Test fun reconOpenOnAnotherScreenNotifiesInsteadOfPopping() = assertEquals(Action.NOTIFY, decide(app = true))

    @Test fun theSheetShowingTheJobNeedsNothing() {
        assertEquals(Action.NOTHING, decide(sheet = true))
        assertEquals(Action.NOTHING, decide(state = JobState.FAILED, sheet = true))
    }
}

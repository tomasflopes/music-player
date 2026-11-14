package dev.tomasflopes.musicplayer.core.model.undo

class SingleUndoSlotTest : UndoSlotContract() {
    override fun slot(): UndoSlot = SingleUndoSlot()
}

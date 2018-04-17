package org.nus.gmx.cs3218project

sealed class AlphanumericGuess(val frequency: Float) {
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return this.javaClass.simpleName.hashCode()
    }
}

class StartTransmission(frequency: Float): AlphanumericGuess(frequency)
class EndTransmission(frequency: Float): AlphanumericGuess(frequency)
class Next(frequency: Float): AlphanumericGuess(frequency)
class Character(frequency: Float, val character: Char): AlphanumericGuess(frequency) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as Character

        if (character != other.character) return false

        return true
    }

    override fun hashCode(): Int {
        return character.hashCode()
    }
}
class Unknown(frequency: Float): AlphanumericGuess(frequency)

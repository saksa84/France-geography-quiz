package io.github.saksa84.francemapquiz.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.saksa84.francemapquiz.R

/**
 * Sons de bonne et de mauvaise réponse (fichiers de `res/raw`, générés par `tools/build_sounds.py`).
 *
 * SoundPool est le lecteur d'Android pour les sons courts : ils sont décodés une fois au démarrage
 * et partent sans délai. Le canal « jeu » suit le volume des médias.
 */
class AnswerSounds(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val correct = pool.load(context, R.raw.answer_correct, 1)
    private val wrong = pool.load(context, R.raw.answer_wrong, 1)

    fun play(isCorrect: Boolean) {
        pool.play(if (isCorrect) correct else wrong, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

package dev.darkx.visuals.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Вывод звука. Вся работа с javax.sound — только здесь: если в рантайме нет модуля java.desktop,
 * ошибка случится при создании этого класса и будет поймана, а остальной клиент не заденет.
 */
final class LineOut {
	private final SourceDataLine line;

	LineOut(int rate, int bufferBytes) throws Exception {
		AudioFormat fmt = new AudioFormat(rate, 16, 2, true, false);
		line = AudioSystem.getSourceDataLine(fmt);
		line.open(fmt, bufferBytes);
	}

	void start() { line.start(); }

	void drainAndStop() {
		line.drain();
		line.stop();
	}

	void write(byte[] data) { line.write(data, 0, data.length); }

	void close() { line.close(); }
}

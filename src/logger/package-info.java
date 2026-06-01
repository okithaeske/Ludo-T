/**
 * Event publishing and logging (Observer / Publish-Subscribe pattern).
 *
 * <p><b>Start here:</b> {@link logger.GameEventPublisher} is the subject. Engine classes call
 * {@code publish*} methods on it; registered listeners receive the corresponding
 * {@code on*} callback.
 *
 * <h2>Class responsibilities</h2>
 * <ul>
 *   <li>{@link logger.GameEventListener} &ndash; Observer interface; one method per game
 *       event (roll, move, capture, win, round summary, etc.).</li>
 *   <li>{@link logger.GameEventPublisher} &ndash; Subject: maintains the listener list and
 *       fans each event out to all registered listeners.</li>
 *   <li>{@link logger.Logger} &ndash; Concrete observer that prints every event to
 *       {@code System.out}. The only registered listener in the default configuration.</li>
 * </ul>
 *
 * <h2>Design pattern</h2>
 * <em>Observer / Publish-Subscribe:</em> engine classes are fully decoupled from output
 * format. Swap or add a {@code GameEventListener} without touching engine code.
 */
package logger;

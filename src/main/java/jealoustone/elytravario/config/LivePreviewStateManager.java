package jealoustone.elytravario.config;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.isxander.yacl3.api.StateManager;

/** A YACL state manager that previews changes immediately and rebases after each save. */
final class LivePreviewStateManager<T> implements StateManager<T> {
	private final T defaultValue;
	private final Consumer<T> preview;
	private T savedValue;
	private T pendingValue;
	private StateListener<T> listener = StateListener.noop();

	LivePreviewStateManager(T defaultValue, Supplier<T> currentValue, Consumer<T> preview) {
		this.defaultValue = defaultValue;
		this.preview = preview;
		this.savedValue = currentValue.get();
		this.pendingValue = savedValue;
	}

	@Override
	public void set(T value) {
		if (Objects.equals(pendingValue, value)) return;
		T previous = pendingValue;
		pendingValue = value;
		preview.accept(value);
		listener.onStateChange(previous, value);
	}

	@Override public T get() { return pendingValue; }

	@Override
	public void apply() {
		savedValue = pendingValue;
	}

	@Override
	public void resetToDefault(ResetAction action) {
		set(defaultValue);
	}

	@Override
	public void sync() {
		set(savedValue);
	}

	@Override
	public boolean isSynced() {
		return Objects.equals(savedValue, pendingValue);
	}

	@Override
	public boolean isDefault() {
		return Objects.equals(defaultValue, pendingValue);
	}

	@Override
	public void addListener(StateListener<T> listener) {
		this.listener = this.listener.andThen(listener);
	}
}

package adflixit.shared;

public class Poolable {
	protected boolean free = true;

	public void release() {
		free = true;
	}

	public boolean isFree() {
		return free;
	}
}

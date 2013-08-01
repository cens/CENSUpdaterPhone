package edu.ucla.cens.Updater.model;


public interface EasyObservable<T> {
	
	void addListener(OnChangeListener<T> listener);
	void removeListener(OnChangeListener<T> listener);
	
}
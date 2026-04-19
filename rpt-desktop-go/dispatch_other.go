//go:build !darwin

package main

func dispatchOnMainThread(fn func()) {
	fn()
}

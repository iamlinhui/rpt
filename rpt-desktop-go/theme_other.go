//go:build !windows

package main

func setWindowDarkMode(hwnd uintptr, dark bool) {}
func findAppWindow() uintptr                    { return 0 }

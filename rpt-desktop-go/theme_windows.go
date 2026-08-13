//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

var (
	dwmapi                    = syscall.NewLazyDLL("dwmapi.dll")
	procDwmSetWindowAttribute = dwmapi.NewProc("DwmSetWindowAttribute")
	user32                    = syscall.NewLazyDLL("user32.dll")
	procFindWindowW           = user32.NewProc("FindWindowW")
)

func setWindowDarkMode(hwnd uintptr, dark bool) {
	var val int32
	if dark {
		val = 1
	}
	procDwmSetWindowAttribute.Call(
		hwnd,
		20, // DWMWA_USE_IMMERSIVE_DARK_MODE
		uintptr(unsafe.Pointer(&val)),
		4, // sizeof(int32)
	)
}

func findAppWindow() uintptr {
	title, _ := syscall.UTF16PtrFromString(appTitle)
	hwnd, _, _ := procFindWindowW.Call(0, uintptr(unsafe.Pointer(title)))
	return hwnd
}

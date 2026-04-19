package main

/*
#cgo LDFLAGS: -framework Foundation
#include <dispatch/dispatch.h>

extern void goMainThreadCallback(void *ctx);

static void dispatchMainThread() {
    dispatch_async_f(dispatch_get_main_queue(), NULL, (dispatch_function_t)goMainThreadCallback);
}
*/
import "C"
import "unsafe"

var mainThreadCh = make(chan func(), 1)

//export goMainThreadCallback
func goMainThreadCallback(_ unsafe.Pointer) {
	select {
	case fn := <-mainThreadCh:
		fn()
	default:
	}
}

func dispatchOnMainThread(fn func()) {
	mainThreadCh <- fn
	C.dispatchMainThread()
}

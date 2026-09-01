module rpt-desktop-go

go 1.26.0

require (
	github.com/energye/systray v1.0.3
	github.com/wailsapp/wails/v2 v2.15.0
	golang.org/x/image v0.45.0
	gopkg.in/yaml.v3 v3.0.1
	rpt-client-go v0.0.0
)

replace rpt-client-go => ../rpt-client-go

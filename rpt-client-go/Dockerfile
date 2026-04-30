# Dockerfile for rpt-client-go
# 1. Build with: docker build -f Dockerfile -t rpt-client-go .
# 2. Run with: docker run -d --network host -v /opt/rpt/conf:/home/rpt/conf --restart=always --name rpt-client-go rpt-client-go

FROM golang:1.26-alpine AS builder

WORKDIR /build

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o rpt-client-go .

FROM alpine:latest
LABEL authors="lynn"

ENV TIME_ZONE=PRC
RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/"$TIME_ZONE" /etc/localtime \
    && echo "$TIME_ZONE" > /etc/timezone

COPY --from=builder /build/rpt-client-go /home/rpt/rpt-client-go

WORKDIR /home/rpt

ENTRYPOINT ["./rpt-client-go", "-config", "./conf/client.yml"]

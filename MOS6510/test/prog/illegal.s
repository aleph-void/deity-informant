; Exercises every undocumented NMOS 6510 instruction group with fixed operands and logs A, X, Y and P after each
; step to $0400.. so the log can be compared between cc65's sim65 (6502X) and Ghidra's p-code emulator.
; Original test program (Ghidra-Rare), Apache-2.0.  Assemble: ca65 --cpu 6502X; link with illegal.cfg.
        .setcpu "6502X"
LOG     = $0400

        .macro  LOGSTATE n
        php
        sta     LOG+n*4
        stx     LOG+n*4+1
        sty     LOG+n*4+2
        pla
        sta     LOG+n*4+3
        .endmacro

        .code
        ; known data
        lda     #$00
        sta     $20
        lda     #$03
        sta     $21             ; ($20) -> $0300
        ldx     #$05
        lda     #$a5
        sta     $0300
        sta     $0305
        lda     #$3c
        sta     $30
        sta     $35
        cld
        clc
; --- LAX / SAX
        lax     $30             ; A=X=$3c
        LOGSTATE 0
        lax     $0300           ; A=X=$a5
        LOGSTATE 1
        ldy     #$05
        lax     ($20),y         ; $0305
        LOGSTATE 2
        ldx     #$05
        sax     $40             ; $a5&$05=$05
        lda     $40
        LOGSTATE 3
; --- RMW family: SLO RLA SRE RRA DCP ISC on zero page and absolute,X
        lda     #$41
        sta     $50
        lda     #$0f
        sec
        slo     $50
        LOGSTATE 4
        lda     $50
        LOGSTATE 5
        lda     #$ff
        sec
        rla     $50
        LOGSTATE 6
        lda     #$ff
        sre     $50
        LOGSTATE 7
        lda     #$20
        clc
        rra     $50
        LOGSTATE 8
        lda     #$10
        dcp     $50
        LOGSTATE 9
        sec
        lda     #$80
        isc     $50
        LOGSTATE 10
        ldx     #$05
        lda     #$11
        slo     $0300,x
        LOGSTATE 11
        lda     #$77
        sre     $0300,x
        LOGSTATE 12
        ldy     #$05
        lda     #$33
        rla     $0300,y
        LOGSTATE 13
        dcp     ($20),y
        LOGSTATE 14
        isc     ($20),y
        LOGSTATE 15
; --- decimal-mode ISC / RRA
        sed
        sec
        lda     #$46
        ldx     #$12
        stx     $60
        isc     $60
        LOGSTATE 16
        clc
        lda     #$58
        rra     $60
        LOGSTATE 17
        cld
; --- immediate group
        lda     #$ff
        anc     #$80
        LOGSTATE 18
        lda     #$ff
        alr     #$0f
        LOGSTATE 19
        sec
        lda     #$ff
        arr     #$ff
        LOGSTATE 20
        clc
        lda     #$7f
        arr     #$f0
        LOGSTATE 21
        sed
        clc
        lda     #$99
        arr     #$99
        LOGSTATE 22
        cld
        lda     #$ff
        ldx     #$0f
        axs     #$05
        LOGSTATE 23
        lda     #$00
        ldx     #$ff
        ane     #$ff
        LOGSTATE 24
        lda     #$00
        .byte   $ab,$0f         ; LXA #$0f (not known to ca65)
        LOGSTATE 25
        sec
        lda     #$50
        .byte   $eb,$30         ; SBC #$30 (undocumented alias)
        LOGSTATE 26
; --- LAS
        ldy     #$00
        las     $0300,y
        LOGSTATE 27
        ldx     #$ff
        txs
; --- NOPs
        lda     #$12
        .byte   $1a
        .byte   $80,$55
        .byte   $04,$30
        .byte   $0c,$00,$03
        .byte   $14,$30
        .byte   $1c,$00,$03
        LOGSTATE 28
; --- unstable stores (documented constants), no page crossing
        lda     #$ff
        ldx     #$ff
        ldy     #$01
        sha     $0300,y
        lda     $0301
        LOGSTATE 29
        ldx     #$01
        ldy     #$ff
        shy     $0300,x
        lda     $0301
        LOGSTATE 30
        ldx     #$ff
        ldy     #$01
        shx     $0300,y
        lda     $0301
        LOGSTATE 31
        lda     #$f0
        ldx     #$3f
        tas     $0300,y
        tsx
        LOGSTATE 32
        ldx     #$ff
        txs
; --- flags preserved by SAX/NOP, Z/N from LAX
        lda     #$00
        ldx     #$00
        sax     $70
        LOGSTATE 33
        lda     #$80
        sta     $71
        lax     $71
        LOGSTATE 34
        .byte   $ea             ; padding: end of program (the harness appends the tail)

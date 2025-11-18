; Boot record implemenation for S16

org 7C00h

start:
    cli ; disable interrupts as they aren't needed during this stage and only cause problems
    xor ax, ax
    mov ds, ax
    mov es, ax

    mov ss, ax
    mov sp, 7C00h

    mov ax, 5
    mov dh, 1
    mov bx, 7E00h
    call read ; read the root directory into memory

    mov cx, 128 ; the entire root directory
system:
    ; scan the root directory to find S16's system file

    test byte [bx + 0Eh], 08h ; In-use?
    jz skip
    test byte [bx + 0Eh], 01h ; Read-only?
    jz skip
    test byte [bx + 0Eh], 02h ; System?
    jz skip

    mov si, s16system
    mov di, bx
    mov cx, 12
    repe cmpsb
    jz skip

    ; file was found!

    mov si, bx

    mov ax, 1
    mov dh, 4
    mov bx, 8600h
    call read  ; read the reserved blocks for the block tables into memory

    mov ax, [si + 0Ch] 
    shl ax, 6 ; get the byte offset for the block table
    add bx, ax

    mov bp, [si + 0Fh] ; I ran out of registers, so I have to use bp as a counter
    mov si, bx
    mov bx, 0500h
loadblock:

    mov ax, [si]
    mov dh, 1
    call read ; load block
    
    add bx, 800h
    add si, 2
    dec bp
    jnz loadblock

    jmp 0000:0500h ; far jump to the start of the file
skip:
    add bx, 16
    loop system

error: ; I tried to make this as small as possible byte-wise
    mov ax, 0003h ; set video mode to 80x25
    int 10h

    mov ah, 0Eh
    xor bh, bh
    mov si, errormsg
    mov cx, 38
writescreen:
    lodsb
    int 10h
    loop writescreen

    xor ah, ah ; wait for key press and then cold reboot
    int 16h
    int 19h

read: ; A simple function to read the diskette in blocks and use block addressing

    mov cl, [01fdh] ; bitpack containing zero based number of heads and sectors per track
    mov ch, cl
    shr cl, 6 ; shift bit 6 to bit 0 and discard the rest
    and ch, 3Fh ; discard the number of heads

    ; this is basically just lbs to chs math

    shl ch, cl ; sectors per track << number of heads
    shl ax, 2
    div ch  ; get the track

    shr ch, cl ; get back sectors per track
    mov cl, ch
    mov ch, al ; put the track into "ch"
    mov al, ah ; use the reminader of track
    xor ah, ah
    div cl
    xchg dh, al ; swap blocks to read with head
    shl al, 2

    inc ah
    mov cl, ah ; put sector into "cl"

    mov ah, 02h
    int 13h

    jc error ; handle the error directly to save bytes
    ret

s16system: db "S16 system  "
errormsg: db "Boot error!", 0Ah, 0Dh
errorhelp: db "Press any key to reboot.."
db 495 - ($ - $$) dup(0) ; Pad to 495 bytes
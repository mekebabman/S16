; Boot record implemenation for S16

org 7C00h

start:
    cli ; disable interrupts as they aren't needed during this stage and only cause problems
    xor ax, ax
    mov ds, ax
    mov es, ax

    mov ss, ax
    mov sp, 7C00h

    mov ax, 20
    mov dh, 4
    mov bx, 7E00h
    call read ; read the root directory into memory

    mov cx, 128 ; scan through all 128 file tables
system:
    ; scan the root directory to find S16's system file

    cmp byte [bx + 0Eh], 0Bh ; Is it marked with attributes: in-use, system, and read-only? Is it not marked with attributes: executable?
    jne skip

    mov si, s16system
    mov di, bx
    mov cx, 12
    repe cmpsb
    jnz skip

    ; file was found!

    mov si, bx

    mov al, [si + 0Ch] 
    xor ah, ah
    shl ax, 6 ; get the byte offset for the block table
    mov bp, ax
    shr ax, 11 ; get the block to read
    inc al
    shl ax, 2 ; convert block into 512 byte sector
    and bp, 63 ; get the byte offset in the block
    mov dh, 4
    mov bx, 8600h
    call read
    add bx, bp
    
    mov al, [si + 0Fh]
    xor ah, ah
    mov bp, ax ; I ran out of registers, so I have to use bp as a counter
    mov si, bx
    mov bx, 0500h
loadblock:

    mov ax, [si]
    shl ax, 2 ; convert block into 512 byte sector
    mov dh, 4
    call read ; load block
    
    add bx, 800h
    add si, 2
    dec bp
    jnz loadblock

    jmp 0000h:0500h ; far jump to the start of the file
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

    sti ; enable interrupts to get keyboard input
    xor ah, ah ; wait for key press and then cold reboot
    int 16h
    int 19h

read:

    mov cl, [7C00h + 01fdh] ; bitpack containing zero based number of heads and sectors per track
    mov ch, cl
    and ch, 3Fh ; discard number of heads
    shr cl, 6 ; shift bit 6 to bit 0 and discard the rest

    mov di, cx ; preserve the sectors per track in "di"
    shl ch, cl ; sectors per track << number of heads (zero based)
    inc cl 
    add ch, cl ; same result as (SPT * HPC)
    div ch ; cylinder

    mov cx, di ; restore
    mov cl, ch
    inc cl
    mov ch, al ; put cylinder in the right place
    mov al, ah
    xor ah, ah
    div cl ; head
    xchg dh, al ; swap blocks to read with head

    inc ah
    mov cl, ah ; put sector in the right place

    mov ah, 02h
    int 13h

    jc error ; handle the error directly to save bytes
    ret

s16system: db "system      "
errormsg: db "Boot error!", 0Ah, 0Dh
errorhelp: db "Press any key to reboot.."
db 495 - ($ - $$) dup(0) ; Pad to 495 bytes

; db "testdiskette" ; oem
;  
; dw 720 ; total reserved blocks
;  
; db 51h ; number of heads and sectors per track bitpacked
;  
; db 55h, 0AAh ; boot signature
;  
; Free/used blocks
; db 0FEh
; db 255 dup(0)
;  
; Free/used block tables
; db 80h
; db 15 dup(0)
;  
; Free/used file tables
; db 80h
; db 15 dup(0)
;  
; Reserved
; db 1248 dup(0)
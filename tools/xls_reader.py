"""Lecteur minimal de classeurs Excel 97-2003 (.xls, format BIFF8), bibliothèque standard seulement.

Sert à lire le périmètre des massifs de l'ANCT, publié uniquement dans ce format. Il ne lit que les
valeurs des cellules (texte et nombres) de toutes les feuilles : ni formules, ni dates, ni mise en forme.

    from xls_reader import read_sheets
    sheets = read_sheets(path)            # {nom de feuille: [[cellules de la ligne 0], ...]}
"""

import struct


def _compound_stream(data, wanted="Workbook"):
    """Extrait un flux d'un fichier composé OLE2 (le conteneur des .xls)."""
    if data[:8] != b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1":
        raise ValueError("pas un fichier .xls (OLE2)")
    sector = 1 << struct.unpack_from("<H", data, 30)[0]
    mini_sector = 1 << struct.unpack_from("<H", data, 32)[0]
    n_fat, first_dir, _, mini_cutoff, first_mini_fat, n_mini_fat, first_difat, n_difat = struct.unpack_from("<IIIIIIII", data, 44)

    def read_sector(i):
        return data[(i + 1) * sector:(i + 2) * sector]

    difat = list(struct.unpack_from("<109I", data, 76))
    d = first_difat
    for _ in range(n_difat):
        block = read_sector(d)
        values = struct.unpack(f"<{sector // 4}I", block)
        difat += values[:-1]
        d = values[-1]
    fat = []
    for s in difat[:n_fat]:
        fat += struct.unpack(f"<{sector // 4}I", read_sector(s))

    def chain(start):
        out, s = [], start
        while s < 0xFFFFFFFA:
            out.append(s)
            s = fat[s]
        return out

    def read_chain(start):
        return b"".join(read_sector(s) for s in chain(start))

    directory = read_chain(first_dir)
    entries = []
    for off in range(0, len(directory), 128):
        e = directory[off:off + 128]
        name_len = struct.unpack_from("<H", e, 64)[0]
        name = e[:max(0, name_len - 2)].decode("utf-16-le")
        start, size = struct.unpack_from("<II", e, 116)
        entries.append((name, e[66], start, size))
    root = entries[0]
    for name, kind, start, size in entries:
        if name in (wanted, "Book") and kind == 2:
            if size >= mini_cutoff:
                return read_chain(start)[:size]
            mini_stream = read_chain(root[2])
            mini_fat = []
            for s in chain(first_mini_fat) if n_mini_fat else []:
                mini_fat += struct.unpack(f"<{sector // 4}I", read_sector(s))
            out, s = b"", start
            while s < 0xFFFFFFFA:
                out += mini_stream[s * mini_sector:(s + 1) * mini_sector]
                s = mini_fat[s]
            return out[:size]
    raise ValueError("flux Workbook introuvable")


def _records(stream):
    pos = 0
    while pos + 4 <= len(stream):
        rid, length = struct.unpack_from("<HH", stream, pos)
        yield pos, rid, stream[pos + 4:pos + 4 + length]
        pos += 4 + length


def _rk(value):
    """Nombre compressé RK."""
    if value & 2:
        number = float(value >> 2 if not value & 0x80000000 else (value >> 2) - (1 << 30))
    else:
        number = struct.unpack("<d", struct.pack("<Q", (value & 0xFFFFFFFC) << 32))[0]
    return number / 100 if value & 1 else number


def _shared_strings(parts):
    """Table SST : chaînes réparties sur l'enregistrement SST et ses CONTINUE (qui repartent avec un octet d'options)."""
    strings = []
    data, idx = parts[0], 0
    count = struct.unpack_from("<I", data, 4)[0]
    pos = 8

    def need(n):
        nonlocal data, pos, idx
        if pos + n <= len(data):
            return
        idx += 1
        data, pos = data[pos:] + parts[idx], 0

    while len(strings) < count:
        need(3)
        chars, flags = struct.unpack_from("<HB", data, pos)
        pos += 3
        rich = runs = 0
        if flags & 8:
            need(2); rich = struct.unpack_from("<H", data, pos)[0]; pos += 2
        if flags & 4:
            need(4); runs = struct.unpack_from("<I", data, pos)[0]; pos += 4
        wide = flags & 1
        text = ""
        while len(text) < chars:
            if pos >= len(data):  # la chaîne continue dans l'enregistrement CONTINUE suivant
                idx += 1
                data, pos = parts[idx], 1
                wide = parts[idx][0] & 1
            left = chars - len(text)
            if wide:
                n = min(left, (len(data) - pos) // 2)
                text += data[pos:pos + 2 * n].decode("utf-16-le")
                pos += 2 * n
            else:
                n = min(left, len(data) - pos)
                text += data[pos:pos + n].decode("latin-1")
                pos += n
        skip = rich * 4 + runs
        while skip:
            if pos >= len(data):
                idx += 1
                data, pos = parts[idx], 0
            step = min(skip, len(data) - pos)
            pos += step
            skip -= step
        strings.append(text)
    return strings


def read_sheets(path):
    stream = _compound_stream(open(path, "rb").read())
    records = list(_records(stream))
    sst, sheets_meta = [], []
    for i, (pos, rid, body) in enumerate(records):
        if rid == 0x00FC:  # SST
            parts = [body]
            j = i + 1
            while j < len(records) and records[j][1] == 0x003C:  # CONTINUE
                parts.append(records[j][2])
                j += 1
            sst = _shared_strings(parts)
        elif rid == 0x0085:  # BOUNDSHEET
            offset = struct.unpack_from("<I", body, 0)[0]
            n, wide = body[6], body[7] & 1
            name = body[8:8 + 2 * n].decode("utf-16-le") if wide else body[8:8 + n].decode("latin-1")
            sheets_meta.append((name, offset))
    sheets = {}
    for name, offset in sheets_meta:
        cells = {}
        for pos, rid, body in _records(stream[offset:]):
            if rid == 0x000A:  # EOF de la feuille
                break
            if rid == 0x00FD:  # LABELSST
                r, c, _, k = struct.unpack_from("<HHHI", body)
                cells[(r, c)] = sst[k]
            elif rid == 0x0203:  # NUMBER
                r, c, _ = struct.unpack_from("<HHH", body)
                cells[(r, c)] = struct.unpack_from("<d", body, 6)[0]
            elif rid == 0x027E:  # RK
                r, c, _, v = struct.unpack_from("<HHHI", body)
                cells[(r, c)] = _rk(v)
            elif rid == 0x00BD:  # MULRK
                r, c = struct.unpack_from("<HH", body)
                for k in range((len(body) - 6) // 6):
                    cells[(r, c + k)] = _rk(struct.unpack_from("<I", body, 4 + 6 * k + 2)[0])
            elif rid == 0x0204:  # LABEL (ancien format)
                r, c, _, n = struct.unpack_from("<HHHH", body)
                wide = body[8] & 1
                cells[(r, c)] = body[9:9 + 2 * n].decode("utf-16-le") if wide else body[9:9 + n].decode("latin-1")
        if cells:
            rows = max(r for r, _ in cells) + 1
            cols = max(c for _, c in cells) + 1
            sheets[name] = [[cells.get((r, c)) for c in range(cols)] for r in range(rows)]
        else:
            sheets[name] = []
    return sheets

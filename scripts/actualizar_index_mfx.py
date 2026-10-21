#!/usr/bin/env python3
"""Actualiza la entrada de una extension en index.json con los
datos del .mfx recien construido y firmado por el CI.

Uso: actualizar_index_mfx.py <id> <version> <sha256hex> <firma_b64>
Idempotente: sobreescribe los campos mfx si el index ya los trae.
"""
import json
import sys

URL_BASE = ("https://raw.githubusercontent.com/harley654321/"
            "mangafox-extensions/builds/mfx/{}.mfx")

def main() -> int:
    if len(sys.argv) != 5:
        print("uso: actualizar_index_mfx.py <id> <version> <sha256> <firma>")
        return 2
    ext_id, version, sha256, firma = sys.argv[1:5]
    with open("index.json", encoding="utf-8") as fh:
        datos = json.load(fh)
    tocado = False
    for entrada in datos.get("extensions", []):
        if entrada.get("id") == ext_id:
            entrada["urlMfx"] = URL_BASE.format(ext_id)
            entrada["fileHashMfx"] = sha256
            entrada["firmaMfx"] = firma
            entrada["versionMfx"] = int(version)
            tocado = True
    if not tocado:
        print("aviso: id no encontrado en index.json:", ext_id)
        return 1
    with open("index.json", "w", encoding="utf-8") as fh:
        json.dump(datos, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("index.json: " + ext_id + " v" + version + " firmado y referenciado")
    return 0

if __name__ == "__main__":
    sys.exit(main())

import os
import sys
from PIL import Image

def process_screenshots():
    base_dir = r"C:\Users\alexp\Documents\Compartilhamento\APP MX61\Imagens MX  Control"
    out_dir = os.path.join(base_dir, "PlayStore_Ready")
    os.makedirs(out_dir, exist_ok=True)

    # Dark background color of the app: #121820 -> (18, 24, 32)
    bg_color = (18, 24, 32)

    tasks = [
        {
            "src": "Screenshot_20261011_075706_MX Control.jpg",
            "dst": "01_mixer_celular.png",
            "type": "vertical",
            "desc": "Mixer 4 Canais (Vertical)"
        },
        {
            "src": "Screenshot_20261011_075731_MX Control.jpg",
            "dst": "02_setlist_celular.png",
            "type": "vertical",
            "desc": "Setlist (Vertical)"
        },
        {
            "src": "Screenshot_20261011_075634_MX Control.jpg",
            "dst": "03_parte_efeitos_celular.png",
            "type": "vertical",
            "desc": "Parte / Efeitos (Vertical)"
        },
        {
            "src": "Screenshot_20261011_075650_MX Control.jpg",
            "dst": "04_filtro_split_celular.png",
            "type": "vertical",
            "desc": "Filtros / Splits (Vertical)"
        },
        {
            "src": "Screenshot_20261011_075717_MX Control.jpg",
            "dst": "05_mixer_tablet_landscape.png",
            "type": "horizontal",
            "desc": "Mixer 8 Canais (Horizontal / Landscape)"
        }
    ]

    results = []

    for task in tasks:
        src_path = os.path.join(base_dir, task["src"])
        dst_path = os.path.join(out_dir, task["dst"])

        if not os.path.exists(src_path):
            print(f"ERRO: Arquivo de origem não encontrado: {src_path}")
            continue

        with Image.open(src_path) as img:
            img = img.convert("RGB")
            orig_w, orig_h = img.size

            if task["type"] == "vertical":
                # Cortar barra superior de status (y: 0..98) e barra inferior de navegação (y: 2298..2340)
                # Mantém 100% da interface do aplicativo intacta (ConnectionHeader e BottomNavigation)
                crop_box = (0, 98, orig_w, 2298)
                cropped = img.crop(crop_box)

                target_w, target_h = 1080, 2400
                scale = min(target_w / cropped.width, target_h / cropped.height)
                new_w = int(round(cropped.width * scale))
                new_h = int(round(cropped.height * scale))

                if (new_w, new_h) != cropped.size:
                    resized = cropped.resize((new_w, new_h), Image.Resampling.LANCZOS)
                else:
                    resized = cropped

                # Canvas 1080 x 2400 (proporção 9:20) com fundo #121820
                final_canvas = Image.new("RGB", (target_w, target_h), bg_color)
                paste_x = (target_w - new_w) // 2
                paste_y = (target_h - new_h) // 2
                final_canvas.paste(resized, (paste_x, paste_y))

            else:
                # Horizontal (2340 x 1080)
                # Cortar barra de status superior (y: 0..85) e barra inferior de navegação (y: 1015..1080)
                crop_box = (0, 85, orig_w, 1015)
                cropped = img.crop(crop_box)

                target_w, target_h = 1920, 1080
                scale = min(target_w / cropped.width, target_h / cropped.height)
                new_w = int(round(cropped.width * scale))
                new_h = int(round(cropped.height * scale))

                resized = cropped.resize((new_w, new_h), Image.Resampling.LANCZOS)

                # Canvas 1920 x 1080 (proporção 16:9) com fundo #121820
                final_canvas = Image.new("RGB", (target_w, target_h), bg_color)
                paste_x = (target_w - new_w) // 2
                paste_y = (target_h - new_h) // 2
                final_canvas.paste(resized, (paste_x, paste_y))

            # Exportar como PNG 24-bit RGB (sem Alpha)
            final_canvas.save(dst_path, format="PNG", optimize=True)

            file_size_kb = os.path.getsize(dst_path) / 1024
            results.append({
                "file": task["dst"],
                "desc": task["desc"],
                "res": f"{final_canvas.width} x {final_canvas.height}",
                "mode": final_canvas.mode,
                "size_kb": f"{file_size_kb:.1f} KB",
                "path": dst_path
            })

    print("\n" + "=" * 80)
    print("PROCESSAMENTO DE SCREENSHOTS CONCLUÍDO COM SUCESSO!")
    print("=" * 80)
    for r in results:
        print(f"-> {r['file']} [{r['desc']}]")
        print(f"   Resolução: {r['res']} | Modo: {r['mode']} | Tamanho: {r['size_kb']}")
        print(f"   Caminho: {r['path']}")
    print("=" * 80 + "\n")

if __name__ == "__main__":
    process_screenshots()

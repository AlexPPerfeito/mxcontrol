import re
import os
from pypdf import PdfReader

pdf_path = os.path.join('Manuais', 'mx49mx61_en_datalist_a0.pdf')
if not os.path.exists(pdf_path):
    raise FileNotFoundError(f"PDF not found at {pdf_path}")

reader = PdfReader(pdf_path)

MAIN_CATS = ['AP', 'KB', 'ORG', 'GTR', 'BAS', 'STR', 'BRS', 'WND', 'LD', 'PAD', 'CMP', 'CP', 'DR', 'SFX', 'MFX', 'ETH']

current_main = 'AP'
current_sub = ''

parsed_voices = []
voice_id = 1

# Pages 2 to 9 (indices 1 to 8)
for page_idx in range(1, 9):
    page = reader.pages[page_idx]
    lines = page.extract_text().split('\n')
    for line in lines:
        line = line.strip()
        if not line:
            continue
        if 'Data List' in line or 'Voice List' in line or 'Polyphony' in line or '(Main)' in line or '(Sub)' in line or 'Category' in line or 'Voice Name' in line:
            continue
        
        # Fix the single font spacing anomaly on page 7
        if '0 8 6 W i l d  H o r s e' in line or 'Wild  H o r s e' in line:
            line = '086 Wild Horse 63 5 59 2'
        
        m = re.search(r'^(?:(.*?)\s+)?(\d{3})\s+(.*?)\s+(\d+)\s+(\d+)\s+(\d+)\s+([\d\-]+)$', line)
        if m:
            prefix = (m.group(1) or '').strip()
            num_str = m.group(2)
            name = m.group(3).strip()
            msb = int(m.group(4))
            lsb = int(m.group(5))
            pc_raw = int(m.group(6))
            poly = m.group(7)
            
            if prefix:
                tokens = prefix.split()
                if len(tokens) == 2:
                    if tokens[0] in MAIN_CATS:
                        current_main = tokens[0]
                        current_sub = tokens[1]
                    else:
                        current_sub = prefix
                elif len(tokens) == 1:
                    if tokens[0] in MAIN_CATS:
                        current_main = tokens[0]
                        current_sub = ''
                    else:
                        current_sub = tokens[0]
            
            clean_name = name.replace('\\', '\\\\').replace('"', '\\"')
            clean_sub = current_sub.replace('\\', '\\\\').replace('"', '\\"')
            
            parsed_voices.append({
                'id': voice_id,
                'name': clean_name,
                'category': current_main,
                'sub': clean_sub,
                'msb': msb,
                'lsb': lsb,
                'pc_midi': pc_raw - 1
            })
            voice_id += 1

print(f"Extracted {len(parsed_voices)} voices from official Data List PDF.")

out_file = os.path.join('app', 'src', 'main', 'java', 'com', 'yamahamx', 'editor', 'data', 'repository', 'VoiceRepository.kt')

with open(out_file, 'w', encoding='utf-8') as f:
    f.write('package com.yamahamx.editor.data.repository\n\n')
    f.write('import com.yamahamx.editor.data.model.YamahaVoice\n\n')
    f.write('/**\n')
    f.write(' * Official Yamaha MX49 / MX61 / MX88 Factory Preset Voice Catalog.\n')
    f.write(' *\n')
    f.write(f' * Extracted directly from the official Yamaha Data List (mx49mx61_en_datalist_a0.pdf).\n')
    f.write(f' * - Total Voices: {len(parsed_voices)}\n')
    f.write(' * - [programChange] is 0-indexed (0..127) as required by MIDI SysEx / CC protocol (PC_PDF - 1).\n')
    f.write(' * - [displayProgramNumber] is 1-indexed (1..128) matching the Data List display.\n')
    f.write(' */\n')
    f.write('object VoiceRepository {\n\n')
    f.write('    val CATEGORIES = listOf(\n')
    f.write('        "ALL", "AP", "KB", "ORG", "GTR", "BAS", "STR", "BRS",\n')
    f.write('        "WND", "LD", "PAD", "CMP", "CP", "DR", "ETH", "SFX", "MFX"\n')
    f.write('    )\n\n')
    f.write('    private val VOICES: List<YamahaVoice> by lazy {\n')
    f.write('        listOf(\n')
    for v in parsed_voices:
        f.write(f'            YamahaVoice({v["id"]}, "{v["name"]}", "{v["category"]}", "{v["sub"]}", {v["msb"]}, {v["lsb"]}, {v["pc_midi"]}),\n')
    f.write('        )\n')
    f.write('    }\n\n')
    f.write('    fun getAllVoices(): List<YamahaVoice> = VOICES\n\n')
    f.write('    fun getDefaultVoiceForPart(partIndex: Int): YamahaVoice {\n')
    f.write('        return when (partIndex) {\n')
    f.write('            0 -> VOICES.firstOrNull { it.name == "CncrtGrand" } ?: VOICES.first()\n')
    f.write('            1 -> VOICES.firstOrNull { it.name == "Vintage\'74" } ?: VOICES[25]\n')
    f.write('            2 -> VOICES.firstOrNull { it.name == "Jazz Organ 1" || it.category == "ORG" } ?: VOICES[50]\n')
    f.write('            3 -> VOICES.firstOrNull { it.name == "Real Strings" || it.category == "STR" } ?: VOICES[100]\n')
    f.write('            4 -> VOICES.firstOrNull { it.name == "Power Brass" || it.category == "BRS" } ?: VOICES[150]\n')
    f.write('            5 -> VOICES.firstOrNull { it.name == "Classical" || it.category == "GTR" } ?: VOICES[200]\n')
    f.write('            6 -> VOICES.firstOrNull { it.name == "FingerBass" || it.category == "BAS" } ?: VOICES[250]\n')
    f.write('            7 -> VOICES.firstOrNull { it.name == "Sweet DX" || it.category == "KB" } ?: VOICES[20]\n')
    f.write('            8 -> VOICES.firstOrNull { it.name == "AnalogSwp" || it.category == "PAD" } ?: VOICES[300]\n')
    f.write('            9 -> VOICES.firstOrNull { it.category == "DR" } ?: VOICES.last()\n')
    f.write('            else -> VOICES.first()\n')
    f.write('        }\n')
    f.write('    }\n\n')
    f.write('    /**\n')
    f.write('     * Filters voices by query text and optional category.\n')
    f.write('     */\n')
    f.write('    fun searchVoices(query: String = "", category: String? = null): List<YamahaVoice> {\n')
    f.write('        val trimmedQuery = query.trim().lowercase()\n')
    f.write('        val filterCat = if (category.isNullOrBlank() || category.equals("ALL", ignoreCase = true)) null else category.uppercase()\n\n')
    f.write('        return VOICES.filter { voice ->\n')
    f.write('            val matchesCategory = filterCat == null || voice.category.equals(filterCat, ignoreCase = true)\n')
    f.write('            val matchesQuery = trimmedQuery.isEmpty() ||\n')
    f.write('                    voice.name.lowercase().contains(trimmedQuery) ||\n')
    f.write('                    voice.subCategory.lowercase().contains(trimmedQuery) ||\n')
    f.write('                    voice.bankName.lowercase().contains(trimmedQuery) ||\n')
    f.write('                    voice.category.lowercase().contains(trimmedQuery)\n')
    f.write('            matchesCategory && matchesQuery\n')
    f.write('        }\n')
    f.write('    }\n')
    f.write('}\n')

print(f"Successfully generated {out_file} with {len(parsed_voices)} voices!")

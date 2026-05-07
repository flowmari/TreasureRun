#!/usr/bin/env python3
"""
言語別 ResourcePack ZIP を生成する。
cd TreasureRun-main && python3 scripts/generate_fallback_resourcepacks.py
"""
import hashlib, json, sys, zipfile
from pathlib import Path

LANG_MAP = {"ja":"ja_jp.json","en":"en_us.json","de":"de_de.json","es":"es_es.json","fr":"fr_fr.json","ko":"ko_kr.json","ru":"ru_ru.json","zh_tw":"zh_tw.json","fi":"fi_fi.json","it":"it_it.json","nl":"nl_nl.json","pt":"pt_br.json","hi":"hi_in.json","sv":"sv_se.json","is":"is_is.json","la":"la_la.json","sa":"sa_in.json","lzh":"lzh_hant.json","ojp":"ojp_jp.json","asl_gloss":"asl_us.json"}
LOCALES = ["af_za","ar_sa","az_az","ba_ru","bar","be_by","bg_bg","br_fr","brb","bs_ba","ca_es","cs_cz","cy_gb","da_dk","de_at","de_ch","de_de","el_gr","en_au","en_ca","en_gb","en_nz","en_pt","en_ud","en_us","enp","enws","eo_uy","es_ar","es_cl","es_ec","es_es","es_mx","es_uy","es_ve","esan","et_ee","eu_es","fa_ir","fi_fi","fil_ph","fo_fo","fr_ca","fr_fr","fra_de","fur_it","fy_nl","ga_ie","gd_gb","gl_es","got_de","gr_el","gsw_ch","gu_in","gv_im","ha_ng","he_il","hi_in","hr_hr","hu_hu","hy_am","id_id","ig_ng","io_en","is_is","it_it","ja_jp","jbo_en","ka_ge","kk_kz","kn_in","ko_kr","ksh_de","kw_gb","la_la","lb_lu","li_li","lmo_it","lo_la","lt_lt","lv_lv","lzh_hant","mk_mk","mn_mn","ms_my","mt_mt","my_mm","nl_nl","no_no","oc_fr","ojp_jp","pl_pl","pt_br","pt_pt","qya_aa","ro_ro","rpr","ru_ru","se_no","sk_sk","sl_si","so_so","sq_al","sr_cs","sr_sp","sv_se","sxu_de","szl_pl","ta_in","th_th","tl_ph","tlh_aa","tok","tr_tr","tt_ru","uk_ua","val_es","vec_it","vi_vn","yi_de","yo_ng","zh_cn","zh_tw","zlm_arab","asl_us","sa_in"]
MCMETA = json.dumps({"pack":{"pack_format":34,"supported_formats":[18,34],"description":"TreasureRun i18n fallback"}},indent=2)

def sha1_of(p):
    h=hashlib.sha1()
    with open(p,"rb") as f:
        for c in iter(lambda:f.read(65536),b""): h.update(c)
    return h.hexdigest()

root=Path(__file__).resolve().parent.parent
ld=root/"fabric-i18n-mod/src/main/resources/assets/minecraft/lang"
od=root/"resourcepacks/generated"
if not ld.is_dir(): print(f"ERROR: {ld}",file=sys.stderr); sys.exit(1)
od.mkdir(parents=True,exist_ok=True)
results=[]
for lang,fname in LANG_MAP.items():
    src=ld/fname
    if not src.exists(): print(f"  SKIP {lang}"); continue
    data=src.read_bytes(); json.loads(data)
    out=od/f"treasurerun-i18n-pack-{lang}.zip"
    with zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("pack.mcmeta",MCMETA)
        for loc in LOCALES: zf.writestr(f"assets/minecraft/lang/{loc}.json",data)
    sha1=sha1_of(out)
    results.append((lang,sha1))
    print(f"  {lang}: {out.stat().st_size//1024}KB sha1={sha1}")

print("\n=== config.yml に貼る SHA1 ===")
for lang,sha1 in results: print(f"    {lang}:\n      sha1: \"{sha1}\"")
print(f"\n完了: {len(results)}言語")

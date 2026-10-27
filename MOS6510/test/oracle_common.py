"""Shared text normalisation for the oracle comparisons."""
import re
def num(s):
    s = s.strip()
    if s.startswith('$'): return int(s[1:], 16)
    if s.startswith('0x'): return int(s, 16)
    m = re.fullmatch(r'l([0-9a-f]{4})', s)
    if m: return int(m.group(1), 16)
    raise ValueError(s)
def canon(text, branch_ok=True):
    """-> (mnemonic, mode, value) ; value None if absent"""
    t = text.strip().lower()
    t = re.sub(r'\b[az]:', '', t)
    if not t or t == 'none': return None
    parts = t.split(None, 1)
    mn = parts[0]
    if len(parts) == 1: return (mn, 'impl', None)
    o = re.sub(r'\s+', '', parts[1])
    try:
        if o.startswith('#'): return (mn, 'imm', num(o[1:]))
        m = re.fullmatch(r'\((.+),x\)', o)
        if m: return (mn, 'indx', num(m.group(1)))
        m = re.fullmatch(r'\((.+)\),y', o)
        if m: return (mn, 'indy', num(m.group(1)))
        m = re.fullmatch(r'\((.+)\)', o)
        if m: return (mn, 'ind', num(m.group(1)))
        m = re.fullmatch(r'(.+),([xy])', o)
        if m: return (mn, 'idx' + m.group(2), num(m.group(1)))
        if o == 'a': return (mn, 'acc', None)
        return (mn, 'abs', num(o))
    except ValueError:
        return (mn, 'raw', o)
ALIAS = {'kil': 'jam', 'jam': 'jam', 'ane': 'xaa', 'lxa': 'lax#', 'sha': 'ahx', 'shy': 'shy', 'shx': 'shx', 'tas': 'tas', 'las': 'las',
         'dcp': 'dcp', 'isc': 'isc', 'ins': 'isc', 'isb': 'isc', 'alr': 'alr', 'asr': 'alr', 'anc': 'anc', 'arr': 'arr', 'sbx': 'sbx', 'axs': 'sbx',
         'sre': 'sre', 'lse': 'sre', 'slo': 'slo', 'aso': 'slo', 'rla': 'rla', 'rra': 'rra', 'sax': 'sax', 'aax': 'sax', 'lax': 'lax', 'ahx': 'ahx',
         'xaa': 'xaa', 'tas': 'tas', 'shs': 'tas', 'say': 'shy', 'sya': 'shy', 'sxa': 'shx', 'axa': 'ahx', 'lar': 'las', 'lxa#': 'lax#',
         'atx': 'lax#', 'oal': 'lax#', 'dcm': 'dcp', 'sbc': 'sbc', 'nop': 'nop', 'dop': 'nop', 'top': 'nop', 'skb': 'nop', 'skw': 'nop', 'ase': 'slo'}
def norm(c, ghidra=False):
    if c is None: return None
    mn, mode, v = c
    mn = ALIAS.get(mn, mn)
    if mn == 'lax' and mode == 'imm': mn = 'lax#'
    return (mn, mode, v)

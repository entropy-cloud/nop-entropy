/*
  @license
	Rollup.js v3.18.0
	Wed, 01 Mar 2023 18:45:12 GMT - commit 25bdc129d21685b69a00ee55397d42ac6eff6449

	https://github.com/rollup/rollup

	Released under the MIT License.
*/
var e = "3.18.0";
function t(e3) {
  return e3 && e3.__esModule && Object.prototype.hasOwnProperty.call(e3, "default") ? e3.default : e3;
}
var s = {};
!function(e3) {
  const t2 = ",".charCodeAt(0), s2 = ";".charCodeAt(0), i2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", n2 = new Uint8Array(64), r2 = new Uint8Array(128);
  for (let e4 = 0; e4 < i2.length; e4++) {
    const t3 = i2.charCodeAt(e4);
    n2[e4] = t3, r2[t3] = e4;
  }
  const o2 = "undefined" != typeof TextDecoder ? new TextDecoder() : "undefined" != typeof Buffer ? { decode: (e4) => Buffer.from(e4.buffer, e4.byteOffset, e4.byteLength).toString() } : { decode(e4) {
    let t3 = "";
    for (let s3 = 0; s3 < e4.length; s3++)
      t3 += String.fromCharCode(e4[s3]);
    return t3;
  } };
  function a2(e4) {
    const t3 = new Int32Array(5), s3 = [];
    let i3 = 0;
    do {
      const n3 = l2(e4, i3), r3 = [];
      let o3 = true, a3 = 0;
      t3[0] = 0;
      for (let s4 = i3; s4 < n3; s4++) {
        let i4;
        s4 = h2(e4, s4, t3, 0);
        const l3 = t3[0];
        l3 < a3 && (o3 = false), a3 = l3, c2(e4, s4, n3) ? (s4 = h2(e4, s4, t3, 1), s4 = h2(e4, s4, t3, 2), s4 = h2(e4, s4, t3, 3), c2(e4, s4, n3) ? (s4 = h2(e4, s4, t3, 4), i4 = [l3, t3[1], t3[2], t3[3], t3[4]]) : i4 = [l3, t3[1], t3[2], t3[3]]) : i4 = [l3], r3.push(i4);
      }
      o3 || u2(r3), s3.push(r3), i3 = n3 + 1;
    } while (i3 <= e4.length);
    return s3;
  }
  function l2(e4, t3) {
    const s3 = e4.indexOf(";", t3);
    return -1 === s3 ? e4.length : s3;
  }
  function h2(e4, t3, s3, i3) {
    let n3 = 0, o3 = 0, a3 = 0;
    do {
      const s4 = e4.charCodeAt(t3++);
      a3 = r2[s4], n3 |= (31 & a3) << o3, o3 += 5;
    } while (32 & a3);
    const l3 = 1 & n3;
    return n3 >>>= 1, l3 && (n3 = -2147483648 | -n3), s3[i3] += n3, t3;
  }
  function c2(e4, s3, i3) {
    return !(s3 >= i3) && e4.charCodeAt(s3) !== t2;
  }
  function u2(e4) {
    e4.sort(d2);
  }
  function d2(e4, t3) {
    return e4[0] - t3[0];
  }
  function p2(e4) {
    const i3 = new Int32Array(5), n3 = 16384, r3 = n3 - 36, a3 = new Uint8Array(n3), l3 = a3.subarray(0, r3);
    let h3 = 0, c3 = "";
    for (let u3 = 0; u3 < e4.length; u3++) {
      const d3 = e4[u3];
      if (u3 > 0 && (h3 === n3 && (c3 += o2.decode(a3), h3 = 0), a3[h3++] = s2), 0 !== d3.length) {
        i3[0] = 0;
        for (let e5 = 0; e5 < d3.length; e5++) {
          const s3 = d3[e5];
          h3 > r3 && (c3 += o2.decode(l3), a3.copyWithin(0, r3, h3), h3 -= r3), e5 > 0 && (a3[h3++] = t2), h3 = f2(a3, h3, i3, s3, 0), 1 !== s3.length && (h3 = f2(a3, h3, i3, s3, 1), h3 = f2(a3, h3, i3, s3, 2), h3 = f2(a3, h3, i3, s3, 3), 4 !== s3.length && (h3 = f2(a3, h3, i3, s3, 4)));
        }
      }
    }
    return c3 + o2.decode(a3.subarray(0, h3));
  }
  function f2(e4, t3, s3, i3, r3) {
    const o3 = i3[r3];
    let a3 = o3 - s3[r3];
    s3[r3] = o3, a3 = a3 < 0 ? -a3 << 1 | 1 : a3 << 1;
    do {
      let s4 = 31 & a3;
      a3 >>>= 5, a3 > 0 && (s4 |= 32), e4[t3++] = n2[s4];
    } while (a3 > 0);
    return t3;
  }
  e3.decode = a2, e3.encode = p2, Object.defineProperty(e3, "__esModule", { value: true });
}(s);
class i {
  constructor(e3) {
    this.bits = e3 instanceof i ? e3.bits.slice() : [];
  }
  add(e3) {
    this.bits[e3 >> 5] |= 1 << (31 & e3);
  }
  has(e3) {
    return !!(this.bits[e3 >> 5] & 1 << (31 & e3));
  }
}
let n = class e2 {
  constructor(e3, t2, s2) {
    this.start = e3, this.end = t2, this.original = s2, this.intro = "", this.outro = "", this.content = s2, this.storeName = false, this.edited = false, this.previous = null, this.next = null;
  }
  appendLeft(e3) {
    this.outro += e3;
  }
  appendRight(e3) {
    this.intro = this.intro + e3;
  }
  clone() {
    const t2 = new e2(this.start, this.end, this.original);
    return t2.intro = this.intro, t2.outro = this.outro, t2.content = this.content, t2.storeName = this.storeName, t2.edited = this.edited, t2;
  }
  contains(e3) {
    return this.start < e3 && e3 < this.end;
  }
  eachNext(e3) {
    let t2 = this;
    for (; t2; )
      e3(t2), t2 = t2.next;
  }
  eachPrevious(e3) {
    let t2 = this;
    for (; t2; )
      e3(t2), t2 = t2.previous;
  }
  edit(e3, t2, s2) {
    return this.content = e3, s2 || (this.intro = "", this.outro = ""), this.storeName = t2, this.edited = true, this;
  }
  prependLeft(e3) {
    this.outro = e3 + this.outro;
  }
  prependRight(e3) {
    this.intro = e3 + this.intro;
  }
  split(t2) {
    const s2 = t2 - this.start, i2 = this.original.slice(0, s2), n2 = this.original.slice(s2);
    this.original = i2;
    const r2 = new e2(t2, this.end, n2);
    return r2.outro = this.outro, this.outro = "", this.end = t2, this.edited ? (r2.edit("", false), this.content = "") : this.content = i2, r2.next = this.next, r2.next && (r2.next.previous = r2), r2.previous = this, this.next = r2, r2;
  }
  toString() {
    return this.intro + this.content + this.outro;
  }
  trimEnd(e3) {
    if (this.outro = this.outro.replace(e3, ""), this.outro.length)
      return true;
    const t2 = this.content.replace(e3, "");
    return t2.length ? (t2 !== this.content && this.split(this.start + t2.length).edit("", void 0, true), true) : (this.edit("", void 0, true), this.intro = this.intro.replace(e3, ""), !!this.intro.length || void 0);
  }
  trimStart(e3) {
    if (this.intro = this.intro.replace(e3, ""), this.intro.length)
      return true;
    const t2 = this.content.replace(e3, "");
    return t2.length ? (t2 !== this.content && (this.split(this.end - t2.length), this.edit("", void 0, true)), true) : (this.edit("", void 0, true), this.outro = this.outro.replace(e3, ""), !!this.outro.length || void 0);
  }
};
function r() {
  return "undefined" != typeof window && "function" == typeof window.btoa ? (e3) => window.btoa(unescape(encodeURIComponent(e3))) : "function" == typeof Buffer ? (e3) => Buffer.from(e3, "utf-8").toString("base64") : () => {
    throw new Error("Unsupported environment: `window.btoa` or `Buffer` should be supported.");
  };
}
const o = r();
class a {
  constructor(e3) {
    this.version = 3, this.file = e3.file, this.sources = e3.sources, this.sourcesContent = e3.sourcesContent, this.names = e3.names, this.mappings = s.encode(e3.mappings), void 0 !== e3.x_google_ignoreList && (this.x_google_ignoreList = e3.x_google_ignoreList);
  }
  toString() {
    return JSON.stringify(this);
  }
  toUrl() {
    return "data:application/json;charset=utf-8;base64," + o(this.toString());
  }
}
function l(e3, t2) {
  const s2 = e3.split(/[/\\]/), i2 = t2.split(/[/\\]/);
  for (s2.pop(); s2[0] === i2[0]; )
    s2.shift(), i2.shift();
  if (s2.length) {
    let e4 = s2.length;
    for (; e4--; )
      s2[e4] = "..";
  }
  return s2.concat(i2).join("/");
}
const h = Object.prototype.toString;
function c(e3) {
  return "[object Object]" === h.call(e3);
}
function u(e3) {
  const t2 = e3.split("\n"), s2 = [];
  for (let e4 = 0, i2 = 0; e4 < t2.length; e4++)
    s2.push(i2), i2 += t2[e4].length + 1;
  return function(e4) {
    let t3 = 0, i2 = s2.length;
    for (; t3 < i2; ) {
      const n3 = t3 + i2 >> 1;
      e4 < s2[n3] ? i2 = n3 : t3 = n3 + 1;
    }
    const n2 = t3 - 1;
    return { line: n2, column: e4 - s2[n2] };
  };
}
class d {
  constructor(e3) {
    this.hires = e3, this.generatedCodeLine = 0, this.generatedCodeColumn = 0, this.raw = [], this.rawSegments = this.raw[this.generatedCodeLine] = [], this.pending = null;
  }
  addEdit(e3, t2, s2, i2) {
    if (t2.length) {
      const t3 = [this.generatedCodeColumn, e3, s2.line, s2.column];
      i2 >= 0 && t3.push(i2), this.rawSegments.push(t3);
    } else
      this.pending && this.rawSegments.push(this.pending);
    this.advance(t2), this.pending = null;
  }
  addUneditedChunk(e3, t2, s2, i2, n2) {
    let r2 = t2.start, o2 = true;
    for (; r2 < t2.end; )
      (this.hires || o2 || n2.has(r2)) && this.rawSegments.push([this.generatedCodeColumn, e3, i2.line, i2.column]), "\n" === s2[r2] ? (i2.line += 1, i2.column = 0, this.generatedCodeLine += 1, this.raw[this.generatedCodeLine] = this.rawSegments = [], this.generatedCodeColumn = 0, o2 = true) : (i2.column += 1, this.generatedCodeColumn += 1, o2 = false), r2 += 1;
    this.pending = null;
  }
  advance(e3) {
    if (!e3)
      return;
    const t2 = e3.split("\n");
    if (t2.length > 1) {
      for (let e4 = 0; e4 < t2.length - 1; e4++)
        this.generatedCodeLine++, this.raw[this.generatedCodeLine] = this.rawSegments = [];
      this.generatedCodeColumn = 0;
    }
    this.generatedCodeColumn += t2[t2.length - 1].length;
  }
}
const p = "\n", f = { insertLeft: false, insertRight: false, storeName: false };
class m {
  constructor(e3, t2 = {}) {
    const s2 = new n(0, e3.length, e3);
    Object.defineProperties(this, { original: { writable: true, value: e3 }, outro: { writable: true, value: "" }, intro: { writable: true, value: "" }, firstChunk: { writable: true, value: s2 }, lastChunk: { writable: true, value: s2 }, lastSearchedChunk: { writable: true, value: s2 }, byStart: { writable: true, value: {} }, byEnd: { writable: true, value: {} }, filename: { writable: true, value: t2.filename }, indentExclusionRanges: { writable: true, value: t2.indentExclusionRanges }, sourcemapLocations: { writable: true, value: new i() }, storedNames: { writable: true, value: {} }, indentStr: { writable: true, value: void 0 } }), this.byStart[0] = s2, this.byEnd[e3.length] = s2;
  }
  addSourcemapLocation(e3) {
    this.sourcemapLocations.add(e3);
  }
  append(e3) {
    if ("string" != typeof e3)
      throw new TypeError("outro content must be a string");
    return this.outro += e3, this;
  }
  appendLeft(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byEnd[e3];
    return s2 ? s2.appendLeft(t2) : this.intro += t2, this;
  }
  appendRight(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byStart[e3];
    return s2 ? s2.appendRight(t2) : this.outro += t2, this;
  }
  clone() {
    const e3 = new m(this.original, { filename: this.filename });
    let t2 = this.firstChunk, s2 = e3.firstChunk = e3.lastSearchedChunk = t2.clone();
    for (; t2; ) {
      e3.byStart[s2.start] = s2, e3.byEnd[s2.end] = s2;
      const i2 = t2.next, n2 = i2 && i2.clone();
      n2 && (s2.next = n2, n2.previous = s2, s2 = n2), t2 = i2;
    }
    return e3.lastChunk = s2, this.indentExclusionRanges && (e3.indentExclusionRanges = this.indentExclusionRanges.slice()), e3.sourcemapLocations = new i(this.sourcemapLocations), e3.intro = this.intro, e3.outro = this.outro, e3;
  }
  generateDecodedMap(e3) {
    e3 = e3 || {};
    const t2 = Object.keys(this.storedNames), s2 = new d(e3.hires), i2 = u(this.original);
    return this.intro && s2.advance(this.intro), this.firstChunk.eachNext((e4) => {
      const n2 = i2(e4.start);
      e4.intro.length && s2.advance(e4.intro), e4.edited ? s2.addEdit(0, e4.content, n2, e4.storeName ? t2.indexOf(e4.original) : -1) : s2.addUneditedChunk(0, e4, this.original, n2, this.sourcemapLocations), e4.outro.length && s2.advance(e4.outro);
    }), { file: e3.file ? e3.file.split(/[/\\]/).pop() : null, sources: [e3.source ? l(e3.file || "", e3.source) : null], sourcesContent: e3.includeContent ? [this.original] : [null], names: t2, mappings: s2.raw };
  }
  generateMap(e3) {
    return new a(this.generateDecodedMap(e3));
  }
  _ensureindentStr() {
    void 0 === this.indentStr && (this.indentStr = function(e3) {
      const t2 = e3.split("\n"), s2 = t2.filter((e4) => /^\t+/.test(e4)), i2 = t2.filter((e4) => /^ {2,}/.test(e4));
      if (0 === s2.length && 0 === i2.length)
        return null;
      if (s2.length >= i2.length)
        return "	";
      const n2 = i2.reduce((e4, t3) => {
        const s3 = /^ +/.exec(t3)[0].length;
        return Math.min(s3, e4);
      }, 1 / 0);
      return new Array(n2 + 1).join(" ");
    }(this.original));
  }
  _getRawIndentString() {
    return this._ensureindentStr(), this.indentStr;
  }
  getIndentString() {
    return this._ensureindentStr(), null === this.indentStr ? "	" : this.indentStr;
  }
  indent(e3, t2) {
    const s2 = /^[^\r\n]/gm;
    if (c(e3) && (t2 = e3, e3 = void 0), void 0 === e3 && (this._ensureindentStr(), e3 = this.indentStr || "	"), "" === e3)
      return this;
    const i2 = {};
    if ((t2 = t2 || {}).exclude) {
      ("number" == typeof t2.exclude[0] ? [t2.exclude] : t2.exclude).forEach((e4) => {
        for (let t3 = e4[0]; t3 < e4[1]; t3 += 1)
          i2[t3] = true;
      });
    }
    let n2 = false !== t2.indentStart;
    const r2 = (t3) => n2 ? `${e3}${t3}` : (n2 = true, t3);
    this.intro = this.intro.replace(s2, r2);
    let o2 = 0, a2 = this.firstChunk;
    for (; a2; ) {
      const t3 = a2.end;
      if (a2.edited)
        i2[o2] || (a2.content = a2.content.replace(s2, r2), a2.content.length && (n2 = "\n" === a2.content[a2.content.length - 1]));
      else
        for (o2 = a2.start; o2 < t3; ) {
          if (!i2[o2]) {
            const t4 = this.original[o2];
            "\n" === t4 ? n2 = true : "\r" !== t4 && n2 && (n2 = false, o2 === a2.start || (this._splitChunk(a2, o2), a2 = a2.next), a2.prependRight(e3));
          }
          o2 += 1;
        }
      o2 = a2.end, a2 = a2.next;
    }
    return this.outro = this.outro.replace(s2, r2), this;
  }
  insert() {
    throw new Error("magicString.insert(...) is deprecated. Use prependRight(...) or appendLeft(...)");
  }
  insertLeft(e3, t2) {
    return f.insertLeft || (console.warn("magicString.insertLeft(...) is deprecated. Use magicString.appendLeft(...) instead"), f.insertLeft = true), this.appendLeft(e3, t2);
  }
  insertRight(e3, t2) {
    return f.insertRight || (console.warn("magicString.insertRight(...) is deprecated. Use magicString.prependRight(...) instead"), f.insertRight = true), this.prependRight(e3, t2);
  }
  move(e3, t2, s2) {
    if (s2 >= e3 && s2 <= t2)
      throw new Error("Cannot move a selection inside itself");
    this._split(e3), this._split(t2), this._split(s2);
    const i2 = this.byStart[e3], n2 = this.byEnd[t2], r2 = i2.previous, o2 = n2.next, a2 = this.byStart[s2];
    if (!a2 && n2 === this.lastChunk)
      return this;
    const l2 = a2 ? a2.previous : this.lastChunk;
    return r2 && (r2.next = o2), o2 && (o2.previous = r2), l2 && (l2.next = i2), a2 && (a2.previous = n2), i2.previous || (this.firstChunk = n2.next), n2.next || (this.lastChunk = i2.previous, this.lastChunk.next = null), i2.previous = l2, n2.next = a2 || null, l2 || (this.firstChunk = i2), a2 || (this.lastChunk = n2), this;
  }
  overwrite(e3, t2, s2, i2) {
    return i2 = i2 || {}, this.update(e3, t2, s2, { ...i2, overwrite: !i2.contentOnly });
  }
  update(e3, t2, s2, i2) {
    if ("string" != typeof s2)
      throw new TypeError("replacement content must be a string");
    for (; e3 < 0; )
      e3 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    if (t2 > this.original.length)
      throw new Error("end is out of bounds");
    if (e3 === t2)
      throw new Error("Cannot overwrite a zero-length range – use appendLeft or prependRight instead");
    this._split(e3), this._split(t2), true === i2 && (f.storeName || (console.warn("The final argument to magicString.overwrite(...) should be an options object. See https://github.com/rich-harris/magic-string"), f.storeName = true), i2 = { storeName: true });
    const r2 = void 0 !== i2 && i2.storeName, o2 = void 0 !== i2 && i2.overwrite;
    if (r2) {
      const s3 = this.original.slice(e3, t2);
      Object.defineProperty(this.storedNames, s3, { writable: true, value: true, enumerable: true });
    }
    const a2 = this.byStart[e3], l2 = this.byEnd[t2];
    if (a2) {
      let e4 = a2;
      for (; e4 !== l2; ) {
        if (e4.next !== this.byStart[e4.end])
          throw new Error("Cannot overwrite across a split point");
        e4 = e4.next, e4.edit("", false);
      }
      a2.edit(s2, r2, !o2);
    } else {
      const i3 = new n(e3, t2, "").edit(s2, r2);
      l2.next = i3, i3.previous = l2;
    }
    return this;
  }
  prepend(e3) {
    if ("string" != typeof e3)
      throw new TypeError("outro content must be a string");
    return this.intro = e3 + this.intro, this;
  }
  prependLeft(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byEnd[e3];
    return s2 ? s2.prependLeft(t2) : this.intro = t2 + this.intro, this;
  }
  prependRight(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byStart[e3];
    return s2 ? s2.prependRight(t2) : this.outro = t2 + this.outro, this;
  }
  remove(e3, t2) {
    for (; e3 < 0; )
      e3 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    if (e3 === t2)
      return this;
    if (e3 < 0 || t2 > this.original.length)
      throw new Error("Character is out of bounds");
    if (e3 > t2)
      throw new Error("end must be greater than start");
    this._split(e3), this._split(t2);
    let s2 = this.byStart[e3];
    for (; s2; )
      s2.intro = "", s2.outro = "", s2.edit(""), s2 = t2 > s2.end ? this.byStart[s2.end] : null;
    return this;
  }
  lastChar() {
    if (this.outro.length)
      return this.outro[this.outro.length - 1];
    let e3 = this.lastChunk;
    do {
      if (e3.outro.length)
        return e3.outro[e3.outro.length - 1];
      if (e3.content.length)
        return e3.content[e3.content.length - 1];
      if (e3.intro.length)
        return e3.intro[e3.intro.length - 1];
    } while (e3 = e3.previous);
    return this.intro.length ? this.intro[this.intro.length - 1] : "";
  }
  lastLine() {
    let e3 = this.outro.lastIndexOf(p);
    if (-1 !== e3)
      return this.outro.substr(e3 + 1);
    let t2 = this.outro, s2 = this.lastChunk;
    do {
      if (s2.outro.length > 0) {
        if (e3 = s2.outro.lastIndexOf(p), -1 !== e3)
          return s2.outro.substr(e3 + 1) + t2;
        t2 = s2.outro + t2;
      }
      if (s2.content.length > 0) {
        if (e3 = s2.content.lastIndexOf(p), -1 !== e3)
          return s2.content.substr(e3 + 1) + t2;
        t2 = s2.content + t2;
      }
      if (s2.intro.length > 0) {
        if (e3 = s2.intro.lastIndexOf(p), -1 !== e3)
          return s2.intro.substr(e3 + 1) + t2;
        t2 = s2.intro + t2;
      }
    } while (s2 = s2.previous);
    return e3 = this.intro.lastIndexOf(p), -1 !== e3 ? this.intro.substr(e3 + 1) + t2 : this.intro + t2;
  }
  slice(e3 = 0, t2 = this.original.length) {
    for (; e3 < 0; )
      e3 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    let s2 = "", i2 = this.firstChunk;
    for (; i2 && (i2.start > e3 || i2.end <= e3); ) {
      if (i2.start < t2 && i2.end >= t2)
        return s2;
      i2 = i2.next;
    }
    if (i2 && i2.edited && i2.start !== e3)
      throw new Error(`Cannot use replaced character ${e3} as slice start anchor.`);
    const n2 = i2;
    for (; i2; ) {
      !i2.intro || n2 === i2 && i2.start !== e3 || (s2 += i2.intro);
      const r2 = i2.start < t2 && i2.end >= t2;
      if (r2 && i2.edited && i2.end !== t2)
        throw new Error(`Cannot use replaced character ${t2} as slice end anchor.`);
      const o2 = n2 === i2 ? e3 - i2.start : 0, a2 = r2 ? i2.content.length + t2 - i2.end : i2.content.length;
      if (s2 += i2.content.slice(o2, a2), !i2.outro || r2 && i2.end !== t2 || (s2 += i2.outro), r2)
        break;
      i2 = i2.next;
    }
    return s2;
  }
  snip(e3, t2) {
    const s2 = this.clone();
    return s2.remove(0, e3), s2.remove(t2, s2.original.length), s2;
  }
  _split(e3) {
    if (this.byStart[e3] || this.byEnd[e3])
      return;
    let t2 = this.lastSearchedChunk;
    const s2 = e3 > t2.end;
    for (; t2; ) {
      if (t2.contains(e3))
        return this._splitChunk(t2, e3);
      t2 = s2 ? this.byStart[t2.end] : this.byEnd[t2.start];
    }
  }
  _splitChunk(e3, t2) {
    if (e3.edited && e3.content.length) {
      const s3 = u(this.original)(t2);
      throw new Error(`Cannot split a chunk that has already been edited (${s3.line}:${s3.column} – "${e3.original}")`);
    }
    const s2 = e3.split(t2);
    return this.byEnd[t2] = e3, this.byStart[t2] = s2, this.byEnd[s2.end] = s2, e3 === this.lastChunk && (this.lastChunk = s2), this.lastSearchedChunk = e3, true;
  }
  toString() {
    let e3 = this.intro, t2 = this.firstChunk;
    for (; t2; )
      e3 += t2.toString(), t2 = t2.next;
    return e3 + this.outro;
  }
  isEmpty() {
    let e3 = this.firstChunk;
    do {
      if (e3.intro.length && e3.intro.trim() || e3.content.length && e3.content.trim() || e3.outro.length && e3.outro.trim())
        return false;
    } while (e3 = e3.next);
    return true;
  }
  length() {
    let e3 = this.firstChunk, t2 = 0;
    do {
      t2 += e3.intro.length + e3.content.length + e3.outro.length;
    } while (e3 = e3.next);
    return t2;
  }
  trimLines() {
    return this.trim("[\\r\\n]");
  }
  trim(e3) {
    return this.trimStart(e3).trimEnd(e3);
  }
  trimEndAborted(e3) {
    const t2 = new RegExp((e3 || "\\s") + "+$");
    if (this.outro = this.outro.replace(t2, ""), this.outro.length)
      return true;
    let s2 = this.lastChunk;
    do {
      const e4 = s2.end, i2 = s2.trimEnd(t2);
      if (s2.end !== e4 && (this.lastChunk === s2 && (this.lastChunk = s2.next), this.byEnd[s2.end] = s2, this.byStart[s2.next.start] = s2.next, this.byEnd[s2.next.end] = s2.next), i2)
        return true;
      s2 = s2.previous;
    } while (s2);
    return false;
  }
  trimEnd(e3) {
    return this.trimEndAborted(e3), this;
  }
  trimStartAborted(e3) {
    const t2 = new RegExp("^" + (e3 || "\\s") + "+");
    if (this.intro = this.intro.replace(t2, ""), this.intro.length)
      return true;
    let s2 = this.firstChunk;
    do {
      const e4 = s2.end, i2 = s2.trimStart(t2);
      if (s2.end !== e4 && (s2 === this.lastChunk && (this.lastChunk = s2.next), this.byEnd[s2.end] = s2, this.byStart[s2.next.start] = s2.next, this.byEnd[s2.next.end] = s2.next), i2)
        return true;
      s2 = s2.next;
    } while (s2);
    return false;
  }
  trimStart(e3) {
    return this.trimStartAborted(e3), this;
  }
  hasChanged() {
    return this.original !== this.toString();
  }
  _replaceRegexp(e3, t2) {
    function s2(e4, s3) {
      return "string" == typeof t2 ? t2.replace(/\$(\$|&|\d+)/g, (t3, s4) => {
        if ("$" === s4)
          return "$";
        if ("&" === s4)
          return e4[0];
        return +s4 < e4.length ? e4[+s4] : `$${s4}`;
      }) : t2(...e4, e4.index, s3, e4.groups);
    }
    if (e3.global) {
      (function(e4, t3) {
        let s3;
        const i2 = [];
        for (; s3 = e4.exec(t3); )
          i2.push(s3);
        return i2;
      })(e3, this.original).forEach((e4) => {
        null != e4.index && this.overwrite(e4.index, e4.index + e4[0].length, s2(e4, this.original));
      });
    } else {
      const t3 = this.original.match(e3);
      t3 && null != t3.index && this.overwrite(t3.index, t3.index + t3[0].length, s2(t3, this.original));
    }
    return this;
  }
  _replaceString(e3, t2) {
    const { original: s2 } = this, i2 = s2.indexOf(e3);
    return -1 !== i2 && this.overwrite(i2, i2 + e3.length, t2), this;
  }
  replace(e3, t2) {
    return "string" == typeof e3 ? this._replaceString(e3, t2) : this._replaceRegexp(e3, t2);
  }
  _replaceAllString(e3, t2) {
    const { original: s2 } = this, i2 = e3.length;
    for (let n2 = s2.indexOf(e3); -1 !== n2; n2 = s2.indexOf(e3, n2 + i2))
      this.overwrite(n2, n2 + i2, t2);
    return this;
  }
  replaceAll(e3, t2) {
    if ("string" == typeof e3)
      return this._replaceAllString(e3, t2);
    if (!e3.global)
      throw new TypeError("MagicString.prototype.replaceAll called with a non-global RegExp argument");
    return this._replaceRegexp(e3, t2);
  }
}
const g = Object.prototype.hasOwnProperty;
const y = /^(?:\/|(?:[A-Za-z]:)?[/\\|])/, x = /^\.?\.\//, b = /\\/g, E = /[/\\]/, v = /\.[^.]+$/;
function S(e3) {
  return y.test(e3);
}
function k(e3) {
  return x.test(e3);
}
function A(e3) {
  return e3.replace(b, "/");
}
function w(e3) {
  return e3.split(E).pop() || "";
}
function I(e3) {
  const t2 = /[/\\][^/\\]*$/.exec(e3);
  if (!t2)
    return ".";
  return e3.slice(0, -t2[0].length) || "/";
}
function P(e3) {
  const t2 = v.exec(w(e3));
  return t2 ? t2[0] : "";
}
function C(e3, t2) {
  const s2 = e3.split(E).filter(Boolean), i2 = t2.split(E).filter(Boolean);
  for ("." === s2[0] && s2.shift(), "." === i2[0] && i2.shift(); s2[0] && i2[0] && s2[0] === i2[0]; )
    s2.shift(), i2.shift();
  for (; ".." === i2[0] && s2.length > 0; )
    i2.shift(), s2.pop();
  for (; s2.pop(); )
    i2.unshift("..");
  return i2.join("/");
}
function $(...e3) {
  const t2 = e3.shift();
  if (!t2)
    return "/";
  let s2 = t2.split(E);
  for (const t3 of e3)
    if (S(t3))
      s2 = t3.split(E);
    else {
      const e4 = t3.split(E);
      for (; "." === e4[0] || ".." === e4[0]; ) {
        ".." === e4.shift() && s2.pop();
      }
      s2.push(...e4);
    }
  return s2.join("/");
}
const N = /[\n\r'\\\u2028\u2029]/, _ = /([\n\r'\u2028\u2029])/g, T = /\\/g;
function R(e3) {
  return N.test(e3) ? e3.replace(T, "\\\\").replace(_, "\\$1") : e3;
}
function M(e3) {
  const t2 = w(e3);
  return t2.slice(0, Math.max(0, t2.length - P(e3).length));
}
function O(e3) {
  return S(e3) ? C($(), e3) : e3;
}
function D(e3) {
  return "/" === e3[0] || "." === e3[0] && ("/" === e3[1] || "." === e3[1]) || S(e3);
}
const L = /^(\.\.\/)*\.\.$/;
function V(e3, t2, s2, i2) {
  let n2 = A(C(I(e3), t2));
  if (s2 && n2.endsWith(".js") && (n2 = n2.slice(0, -3)), i2) {
    if ("" === n2)
      return "../" + w(t2);
    if (L.test(n2))
      return [...n2.split("/"), "..", w(t2)].join("/");
  }
  return n2 ? n2.startsWith("..") ? n2 : "./" + n2 : ".";
}
class B {
  constructor(e3, t2, s2) {
    this.options = t2, this.inputBase = s2, this.defaultVariableName = "", this.namespaceVariableName = "", this.variableName = "", this.fileName = null, this.importAssertions = null, this.id = e3.id, this.moduleInfo = e3.info, this.renormalizeRenderPath = e3.renormalizeRenderPath, this.suggestedVariableName = e3.suggestedVariableName;
  }
  getFileName() {
    if (this.fileName)
      return this.fileName;
    const { paths: e3 } = this.options;
    return this.fileName = ("function" == typeof e3 ? e3(this.id) : e3[this.id]) || (this.renormalizeRenderPath ? A(C(this.inputBase, this.id)) : this.id);
  }
  getImportAssertions(e3) {
    return this.importAssertions || (this.importAssertions = function(e4, { getObject: t2 }) {
      if (!e4)
        return null;
      const s2 = Object.entries(e4).map(([e5, t3]) => [e5, `'${t3}'`]);
      if (s2.length > 0)
        return t2(s2, { lineBreakIndent: null });
      return null;
    }("es" === this.options.format && this.options.externalImportAssertions && this.moduleInfo.assertions, e3));
  }
  getImportPath(e3) {
    return R(this.renormalizeRenderPath ? V(e3, this.getFileName(), "amd" === this.options.format, false) : this.getFileName());
  }
}
function F(e3, t2, s2) {
  const i2 = e3.get(t2);
  if (void 0 !== i2)
    return i2;
  const n2 = s2();
  return e3.set(t2, n2), n2;
}
function z() {
  return /* @__PURE__ */ new Set();
}
function j() {
  return [];
}
const U = Symbol("Unknown Key"), G = Symbol("Unknown Non-Accessor Key"), W = Symbol("Unknown Integer"), q = Symbol("Symbol.toStringTag"), H = [], K = [U], Y = [G], X = [W], Q = Symbol("Entities");
class J {
  constructor() {
    this.entityPaths = Object.create(null, { [Q]: { value: /* @__PURE__ */ new Set() } });
  }
  trackEntityAtPathAndGetIfTracked(e3, t2) {
    const s2 = this.getEntities(e3);
    return !!s2.has(t2) || (s2.add(t2), false);
  }
  withTrackedEntityAtPath(e3, t2, s2, i2) {
    const n2 = this.getEntities(e3);
    if (n2.has(t2))
      return i2;
    n2.add(t2);
    const r2 = s2();
    return n2.delete(t2), r2;
  }
  getEntities(e3) {
    let t2 = this.entityPaths;
    for (const s2 of e3)
      t2 = t2[s2] = t2[s2] || Object.create(null, { [Q]: { value: /* @__PURE__ */ new Set() } });
    return t2[Q];
  }
}
const Z = new J();
class ee {
  constructor() {
    this.entityPaths = Object.create(null, { [Q]: { value: /* @__PURE__ */ new Map() } });
  }
  trackEntityAtPathAndGetIfTracked(e3, t2, s2) {
    let i2 = this.entityPaths;
    for (const t3 of e3)
      i2 = i2[t3] = i2[t3] || Object.create(null, { [Q]: { value: /* @__PURE__ */ new Map() } });
    const n2 = F(i2[Q], t2, z);
    return !!n2.has(s2) || (n2.add(s2), false);
  }
}
const te = Symbol("Unknown Value"), se = Symbol("Unknown Truthy Value");
class ie {
  constructor() {
    this.included = false;
  }
  deoptimizePath(e3) {
  }
  deoptimizeThisOnInteractionAtPath({ thisArg: e3 }, t2, s2) {
    e3.deoptimizePath(K);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return te;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return re;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return true;
  }
  include(e3, t2, s2) {
    this.included = true;
  }
  includeCallArguments(e3, t2) {
    for (const s2 of t2)
      s2.include(e3, false);
  }
  shouldBeIncluded(e3) {
    return true;
  }
}
const ne = new class extends ie {
}(), re = [ne, false], oe = { thisArg: null, type: 0 }, ae = { args: [ne], thisArg: null, type: 1 }, le = [], he = { args: le, thisArg: null, type: 2, withNew: false };
class ce extends ie {
  constructor(e3) {
    super(), this.name = e3, this.alwaysRendered = false, this.forbiddenNames = null, this.initReached = false, this.isId = false, this.isReassigned = false, this.kind = null, this.renderBaseName = null, this.renderName = null;
  }
  addReference(e3) {
  }
  forbidName(e3) {
    (this.forbiddenNames || (this.forbiddenNames = /* @__PURE__ */ new Set())).add(e3);
  }
  getBaseVariableName() {
    return this.renderBaseName || this.renderName || this.name;
  }
  getName(e3, t2) {
    if (t2 == null ? void 0 : t2(this))
      return this.name;
    const s2 = this.renderName || this.name;
    return this.renderBaseName ? `${this.renderBaseName}${e3(s2)}` : s2;
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }, s2) {
    return 0 !== t2 || e3.length > 0;
  }
  include() {
    this.included = true;
  }
  markCalledFromTryStatement() {
  }
  setRenderNames(e3, t2) {
    this.renderBaseName = e3, this.renderName = t2;
  }
}
class ue extends ce {
  constructor(e3, t2) {
    super(t2), this.referenced = false, this.module = e3, this.isNamespace = "*" === t2;
  }
  addReference(e3) {
    this.referenced = true, "default" !== this.name && "*" !== this.name || this.module.suggestName(e3.name);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > (this.isNamespace ? 1 : 0);
  }
  include() {
    this.included || (this.included = true, this.module.used = true);
  }
}
const de = Object.freeze(/* @__PURE__ */ Object.create(null)), pe = Object.freeze({}), fe = Object.freeze([]);
function me(e3, t2, s2) {
  if ("number" == typeof s2)
    throw new Error("locate takes a { startIndex, offsetLine, offsetColumn } object as the third argument");
  return function(e4, t3) {
    void 0 === t3 && (t3 = {});
    var s3 = t3.offsetLine || 0, i2 = t3.offsetColumn || 0, n2 = e4.split("\n"), r2 = 0, o2 = n2.map(function(e5, t4) {
      var s4 = r2 + e5.length + 1, i3 = { start: r2, end: s4, line: t4 };
      return r2 = s4, i3;
    }), a2 = 0;
    function l2(e5, t4) {
      return e5.start <= t4 && t4 < e5.end;
    }
    function h2(e5, t4) {
      return { line: s3 + e5.line, column: i2 + t4 - e5.start, character: t4 };
    }
    return function(t4, s4) {
      "string" == typeof t4 && (t4 = e4.indexOf(t4, s4 || 0));
      for (var i3 = o2[a2], n3 = t4 >= i3.end ? 1 : -1; i3; ) {
        if (l2(i3, t4))
          return h2(i3, t4);
        i3 = o2[a2 += n3];
      }
    };
  }(e3, s2)(t2, s2 && s2.startIndex);
}
function ge(e3) {
  return e3.replace(/^\t+/, (e4) => e4.split("	").join("  "));
}
const ye = "...";
function xe(e3, t2, s2) {
  let i2 = e3.split("\n");
  const n2 = Math.max(ge(i2[t2 - 1].slice(0, s2)).length + 10 + ye.length, 120), r2 = Math.max(0, t2 - 3);
  let o2 = Math.min(t2 + 2, i2.length);
  for (i2 = i2.slice(r2, o2); !/\S/.test(i2[i2.length - 1]); )
    i2.pop(), o2 -= 1;
  const a2 = String(o2).length;
  return i2.map((e4, i3) => {
    const o3 = r2 + i3 + 1 === t2;
    let l2 = String(i3 + r2 + 1);
    for (; l2.length < a2; )
      l2 = ` ${l2}`;
    let h2 = ge(e4);
    if (h2.length > n2 && (h2 = `${h2.slice(0, n2 - ye.length)}${ye}`), o3) {
      const t3 = function(e5) {
        let t4 = "";
        for (; e5--; )
          t4 += " ";
        return t4;
      }(a2 + 2 + ge(e4.slice(0, s2)).length) + "^";
      return `${l2}: ${h2}
${t3}`;
    }
    return `${l2}: ${h2}`;
  }).join("\n");
}
function be(e3, t2) {
  const s2 = e3.length <= 1, i2 = e3.map((e4) => `"${e4}"`);
  let n2 = s2 ? i2[0] : `${i2.slice(0, -1).join(", ")} and ${i2.slice(-1)[0]}`;
  return t2 && (n2 += ` ${s2 ? t2[0] : t2[1]}`), n2;
}
function Ee(e3) {
  return `https://rollupjs.org/${e3}`;
}
const ve = "configuration-options/#output-amd-id", Se = "configuration-options/#output-dir", ke = "configuration-options/#output-exports", Ae = "configuration-options/#output-format", we = "configuration-options/#output-inlinedynamicimports", Ie = "configuration-options/#output-interop", Pe = "configuration-options/#output-manualchunks", Ce = "configuration-options/#output-name", $e = "configuration-options/#output-sourcemapfile", Ne = "plugin-development/#this-getmoduleinfo";
function _e(e3) {
  throw e3 instanceof Error || (e3 = Object.assign(new Error(e3.message), e3), Object.defineProperty(e3, "name", { value: "RollupError" })), e3;
}
function Te(e3, t2, s2, i2) {
  if ("object" == typeof t2) {
    const { line: s3, column: n2 } = t2;
    e3.loc = { column: n2, file: i2, line: s3 };
  } else {
    e3.pos = t2;
    const { line: n2, column: r2 } = me(s2, t2, { offsetLine: 1 });
    e3.loc = { column: r2, file: i2, line: n2 };
  }
  if (void 0 === e3.frame) {
    const { line: t3, column: i3 } = e3.loc;
    e3.frame = xe(s2, t3, i3);
  }
}
const Re = "INVALID_EXPORT_OPTION", Me = "INVALID_PLUGIN_HOOK", Oe = "INVALID_ROLLUP_PHASE", De = "MISSING_IMPLICIT_DEPENDANT", Le = "MISSING_NAME_OPTION_FOR_IIFE_EXPORT", Ve = "PLUGIN_ERROR", Be = "SOURCEMAP_BROKEN", Fe = "UNEXPECTED_NAMED_IMPORT", ze = "UNRESOLVED_ENTRY", je = "UNRESOLVED_IMPORT";
function Ue(e3) {
  return { code: "CANNOT_CALL_NAMESPACE", message: `Cannot call a namespace ("${e3}").` };
}
function Ge({ fileName: e3, code: t2 }, s2) {
  const i2 = { code: "CHUNK_INVALID", message: `Chunk "${e3}" is not valid JavaScript: ${s2.message}.` };
  return Te(i2, s2.loc, t2, e3), i2;
}
function We(e3) {
  return { code: "CIRCULAR_DEPENDENCY", ids: e3, message: `Circular dependency: ${e3.map(O).join(" -> ")}` };
}
function qe(e3, t2) {
  return { code: "ILLEGAL_REASSIGNMENT", message: `Illegal reassignment of import "${e3}" in "${O(t2)}".` };
}
function He(e3, t2, s2, i2) {
  return { code: "INCONSISTENT_IMPORT_ASSERTIONS", message: `Module "${O(i2)}" tried to import "${O(s2)}" with ${Ke(t2)} assertions, but it was already imported elsewhere with ${Ke(e3)} assertions. Please ensure that import assertions for the same module are always consistent.` };
}
const Ke = (e3) => {
  const t2 = Object.entries(e3);
  return 0 === t2.length ? "no" : t2.map(([e4, t3]) => `"${e4}": "${t3}"`).join(", ");
};
function Ye(e3, t2, s2) {
  return { code: Re, message: `"${e3}" was specified for "output.exports", but entry module "${O(s2)}" has the following exports: ${be(t2)}`, url: Ee(ke) };
}
function Xe(e3, t2, s2, i2) {
  return { code: "INVALID_OPTION", message: `Invalid value ${void 0 === i2 ? "" : `${JSON.stringify(i2)} `}for option "${e3}" - ${s2}.`, url: Ee(t2) };
}
function Qe(e3, t2, s2) {
  const i2 = ".json" === P(s2);
  return { binding: e3, code: "MISSING_EXPORT", exporter: s2, id: t2, message: `"${e3}" is not exported by "${O(s2)}", imported by "${O(t2)}".${i2 ? " (Note that you need @rollup/plugin-json to import JSON files)" : ""}`, url: Ee("troubleshooting/#error-name-is-not-exported-by-module") };
}
function Je(e3) {
  const t2 = [...e3.implicitlyLoadedBefore].map((e4) => O(e4.id)).sort();
  return { code: De, message: `Module "${O(e3.id)}" that should be implicitly loaded before ${be(t2)} is not included in the module graph. Either it was not imported by an included module or only via a tree-shaken dynamic import, or no imported bindings were used and it had otherwise no side-effects.` };
}
function Ze(e3, t2, { hook: s2, id: i2 } = {}) {
  return "string" == typeof e3 && (e3 = { message: e3 }), e3.code && e3.code !== Ve && (e3.pluginCode = e3.code), e3.code = Ve, e3.plugin = t2, s2 && (e3.hook = s2), i2 && (e3.id = i2), e3;
}
function et(e3) {
  return { code: Be, message: `Multiple conflicting contents for sourcemap source ${e3}` };
}
function tt(e3, t2, s2) {
  const i2 = s2 ? "reexport" : "import";
  return { code: Fe, exporter: e3, message: `The named export "${t2}" was ${i2}ed from the external module "${O(e3)}" even though its interop type is "defaultOnly". Either remove or change this ${i2} or change the value of the "output.interop" option.`, url: Ee(Ie) };
}
function st(e3) {
  return { code: Fe, exporter: e3, message: `There was a namespace "*" reexport from the external module "${O(e3)}" even though its interop type is "defaultOnly". This will be ignored as namespace reexports only reexport named exports. If this is not intended, either remove or change this reexport or change the value of the "output.interop" option.`, url: Ee(Ie) };
}
function it(e3) {
  return { code: "VALIDATION_ERROR", message: e3 };
}
function nt(e3, t2, s2, i2, n2) {
  rt(e3, t2, s2, i2.onwarn, i2.strictDeprecations, n2);
}
function rt(e3, t2, s2, i2, n2, r2) {
  if (s2 || n2) {
    const s3 = function(e4, t3, s4) {
      return { code: "DEPRECATED_FEATURE", message: e4, url: Ee(t3), ...s4 ? { plugin: s4 } : {} };
    }(e3, t2, r2);
    if (n2)
      return _e(s3);
    i2(s3);
  }
}
var ot = /* @__PURE__ */ new Set(["await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "eval", "export", "extends", "false", "finally", "for", "function", "if", "implements", "import", "in", "instanceof", "interface", "let", "NaN", "new", "null", "package", "private", "protected", "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"]);
const at = /[^\w$]/g, lt = (e3) => ((e4) => /\d/.test(e4[0]))(e3) || ot.has(e3) || "arguments" === e3;
function ht(e3) {
  return e3 = e3.replace(/-(\w)/g, (e4, t2) => t2.toUpperCase()).replace(at, "_"), lt(e3) && (e3 = `_${e3}`), e3 || "_";
}
class ct {
  constructor(e3, t2, s2, i2, n2, r2) {
    this.options = e3, this.id = t2, this.renormalizeRenderPath = n2, this.dynamicImporters = [], this.execIndex = 1 / 0, this.exportedVariables = /* @__PURE__ */ new Map(), this.importers = [], this.reexported = false, this.used = false, this.declarations = /* @__PURE__ */ new Map(), this.mostCommonSuggestion = 0, this.nameSuggestions = /* @__PURE__ */ new Map(), this.suggestedVariableName = ht(t2.split(/[/\\]/).pop());
    const { importers: o2, dynamicImporters: a2 } = this, l2 = this.info = { assertions: r2, ast: null, code: null, dynamicallyImportedIdResolutions: fe, dynamicallyImportedIds: fe, get dynamicImporters() {
      return a2.sort();
    }, exportedBindings: null, exports: null, hasDefaultExport: null, get hasModuleSideEffects() {
      return nt("Accessing ModuleInfo.hasModuleSideEffects from plugins is deprecated. Please use ModuleInfo.moduleSideEffects instead.", Ne, true, e3), l2.moduleSideEffects;
    }, id: t2, implicitlyLoadedAfterOneOf: fe, implicitlyLoadedBefore: fe, importedIdResolutions: fe, importedIds: fe, get importers() {
      return o2.sort();
    }, isEntry: false, isExternal: true, isIncluded: null, meta: i2, moduleSideEffects: s2, syntheticNamedExports: false };
    Object.defineProperty(this.info, "hasModuleSideEffects", { enumerable: false });
  }
  getVariableForExportName(e3) {
    const t2 = this.declarations.get(e3);
    if (t2)
      return [t2];
    const s2 = new ue(this, e3);
    return this.declarations.set(e3, s2), this.exportedVariables.set(s2, e3), [s2];
  }
  suggestName(e3) {
    const t2 = (this.nameSuggestions.get(e3) ?? 0) + 1;
    this.nameSuggestions.set(e3, t2), t2 > this.mostCommonSuggestion && (this.mostCommonSuggestion = t2, this.suggestedVariableName = e3);
  }
  warnUnusedImports() {
    const e3 = [...this.declarations].filter(([e4, t3]) => "*" !== e4 && !t3.included && !this.reexported && !t3.referenced).map(([e4]) => e4);
    if (0 === e3.length)
      return;
    const t2 = /* @__PURE__ */ new Set();
    for (const s3 of e3)
      for (const e4 of this.declarations.get(s3).module.importers)
        t2.add(e4);
    const s2 = [...t2];
    var i2, n2, r2;
    this.options.onwarn({ code: "UNUSED_EXTERNAL_IMPORT", exporter: i2 = this.id, ids: r2 = s2, message: `${be(n2 = e3, ["is", "are"])} imported from external module "${i2}" but never used in ${be(r2.map((e4) => O(e4)))}.`, names: n2 });
  }
}
const ut = { ArrayPattern(e3, t2) {
  for (const s2 of t2.elements)
    s2 && ut[s2.type](e3, s2);
}, AssignmentPattern(e3, t2) {
  ut[t2.left.type](e3, t2.left);
}, Identifier(e3, t2) {
  e3.push(t2.name);
}, MemberExpression() {
}, ObjectPattern(e3, t2) {
  for (const s2 of t2.properties)
    "RestElement" === s2.type ? ut.RestElement(e3, s2) : ut[s2.value.type](e3, s2.value);
}, RestElement(e3, t2) {
  ut[t2.argument.type](e3, t2.argument);
} }, dt = function(e3) {
  const t2 = [];
  return ut[e3.type](t2, e3), t2;
};
new Set("break case class catch const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield enum await implements package protected static interface private public arguments Infinity NaN undefined null true false eval uneval isFinite isNaN parseFloat parseInt decodeURI decodeURIComponent encodeURI encodeURIComponent escape unescape Object Function Boolean Symbol Error EvalError InternalError RangeError ReferenceError SyntaxError TypeError URIError Number Math Date String RegExp Array Int8Array Uint8Array Uint8ClampedArray Int16Array Uint16Array Int32Array Uint32Array Float32Array Float64Array Map Set WeakMap WeakSet SIMD ArrayBuffer DataView JSON Promise Generator GeneratorFunction Reflect Proxy Intl".split(" ")).add("");
function pt() {
  return { brokenFlow: 0, includedCallArguments: /* @__PURE__ */ new Set(), includedLabels: /* @__PURE__ */ new Set() };
}
function ft() {
  return { accessed: new J(), assigned: new J(), brokenFlow: 0, called: new ee(), ignore: { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: false, this: false }, includedLabels: /* @__PURE__ */ new Set(), instantiated: new ee(), replacedVariableInits: /* @__PURE__ */ new Map() };
}
function mt(e3, t2 = null) {
  return Object.create(t2, e3);
}
const gt = new class extends ie {
  getLiteralValueAtPath() {
  }
}(), yt = { value: { hasEffectsWhenCalled: null, returns: ne } }, xt = new class extends ie {
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 === e3.length ? _t(It, e3[0]) : re;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || Nt(It, e3[0], t2, s2);
  }
}(), bt = { value: { hasEffectsWhenCalled: null, returns: xt } }, Et = new class extends ie {
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 === e3.length ? _t(Pt, e3[0]) : re;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || Nt(Pt, e3[0], t2, s2);
  }
}(), vt = { value: { hasEffectsWhenCalled: null, returns: Et } }, St = new class extends ie {
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 === e3.length ? _t($t, e3[0]) : re;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || Nt($t, e3[0], t2, s2);
  }
}(), kt = { value: { hasEffectsWhenCalled: null, returns: St } }, At = { value: { hasEffectsWhenCalled({ args: e3 }, t2) {
  const s2 = e3[1];
  return e3.length < 2 || "symbol" == typeof s2.getLiteralValueAtPath(H, Z, { deoptimizeCache() {
  } }) && s2.hasEffectsOnInteractionAtPath(H, he, t2);
}, returns: St } }, wt = mt({ hasOwnProperty: bt, isPrototypeOf: bt, propertyIsEnumerable: bt, toLocaleString: kt, toString: kt, valueOf: yt }), It = mt({ valueOf: bt }, wt), Pt = mt({ toExponential: kt, toFixed: kt, toLocaleString: kt, toPrecision: kt, valueOf: vt }, wt), Ct = mt({ exec: yt, test: bt }, wt), $t = mt({ anchor: kt, at: yt, big: kt, blink: kt, bold: kt, charAt: kt, charCodeAt: vt, codePointAt: yt, concat: kt, endsWith: bt, fixed: kt, fontcolor: kt, fontsize: kt, includes: bt, indexOf: vt, italics: kt, lastIndexOf: vt, link: kt, localeCompare: vt, match: yt, matchAll: yt, normalize: kt, padEnd: kt, padStart: kt, repeat: kt, replace: At, replaceAll: At, search: vt, slice: kt, small: kt, split: yt, startsWith: bt, strike: kt, sub: kt, substr: kt, substring: kt, sup: kt, toLocaleLowerCase: kt, toLocaleUpperCase: kt, toLowerCase: kt, toString: kt, toUpperCase: kt, trim: kt, trimEnd: kt, trimLeft: kt, trimRight: kt, trimStart: kt, valueOf: kt }, wt);
function Nt(e3, t2, s2, i2) {
  var _a2, _b;
  return "string" != typeof t2 || !e3[t2] || (((_b = (_a2 = e3[t2]).hasEffectsWhenCalled) == null ? void 0 : _b.call(_a2, s2, i2)) || false);
}
function _t(e3, t2) {
  return "string" == typeof t2 && e3[t2] ? [e3[t2].returns, false] : re;
}
function Tt(e3, t2, s2) {
  s2(e3, t2);
}
function Rt(e3, t2, s2) {
}
var Mt = {};
Mt.Program = Mt.BlockStatement = Mt.StaticBlock = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.body; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Statement");
  }
}, Mt.Statement = Tt, Mt.EmptyStatement = Rt, Mt.ExpressionStatement = Mt.ParenthesizedExpression = Mt.ChainExpression = function(e3, t2, s2) {
  return s2(e3.expression, t2, "Expression");
}, Mt.IfStatement = function(e3, t2, s2) {
  s2(e3.test, t2, "Expression"), s2(e3.consequent, t2, "Statement"), e3.alternate && s2(e3.alternate, t2, "Statement");
}, Mt.LabeledStatement = function(e3, t2, s2) {
  return s2(e3.body, t2, "Statement");
}, Mt.BreakStatement = Mt.ContinueStatement = Rt, Mt.WithStatement = function(e3, t2, s2) {
  s2(e3.object, t2, "Expression"), s2(e3.body, t2, "Statement");
}, Mt.SwitchStatement = function(e3, t2, s2) {
  s2(e3.discriminant, t2, "Expression");
  for (var i2 = 0, n2 = e3.cases; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2.test && s2(r2.test, t2, "Expression");
    for (var o2 = 0, a2 = r2.consequent; o2 < a2.length; o2 += 1) {
      s2(a2[o2], t2, "Statement");
    }
  }
}, Mt.SwitchCase = function(e3, t2, s2) {
  e3.test && s2(e3.test, t2, "Expression");
  for (var i2 = 0, n2 = e3.consequent; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Statement");
  }
}, Mt.ReturnStatement = Mt.YieldExpression = Mt.AwaitExpression = function(e3, t2, s2) {
  e3.argument && s2(e3.argument, t2, "Expression");
}, Mt.ThrowStatement = Mt.SpreadElement = function(e3, t2, s2) {
  return s2(e3.argument, t2, "Expression");
}, Mt.TryStatement = function(e3, t2, s2) {
  s2(e3.block, t2, "Statement"), e3.handler && s2(e3.handler, t2), e3.finalizer && s2(e3.finalizer, t2, "Statement");
}, Mt.CatchClause = function(e3, t2, s2) {
  e3.param && s2(e3.param, t2, "Pattern"), s2(e3.body, t2, "Statement");
}, Mt.WhileStatement = Mt.DoWhileStatement = function(e3, t2, s2) {
  s2(e3.test, t2, "Expression"), s2(e3.body, t2, "Statement");
}, Mt.ForStatement = function(e3, t2, s2) {
  e3.init && s2(e3.init, t2, "ForInit"), e3.test && s2(e3.test, t2, "Expression"), e3.update && s2(e3.update, t2, "Expression"), s2(e3.body, t2, "Statement");
}, Mt.ForInStatement = Mt.ForOfStatement = function(e3, t2, s2) {
  s2(e3.left, t2, "ForInit"), s2(e3.right, t2, "Expression"), s2(e3.body, t2, "Statement");
}, Mt.ForInit = function(e3, t2, s2) {
  "VariableDeclaration" === e3.type ? s2(e3, t2) : s2(e3, t2, "Expression");
}, Mt.DebuggerStatement = Rt, Mt.FunctionDeclaration = function(e3, t2, s2) {
  return s2(e3, t2, "Function");
}, Mt.VariableDeclaration = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.declarations; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, Mt.VariableDeclarator = function(e3, t2, s2) {
  s2(e3.id, t2, "Pattern"), e3.init && s2(e3.init, t2, "Expression");
}, Mt.Function = function(e3, t2, s2) {
  e3.id && s2(e3.id, t2, "Pattern");
  for (var i2 = 0, n2 = e3.params; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Pattern");
  }
  s2(e3.body, t2, e3.expression ? "Expression" : "Statement");
}, Mt.Pattern = function(e3, t2, s2) {
  "Identifier" === e3.type ? s2(e3, t2, "VariablePattern") : "MemberExpression" === e3.type ? s2(e3, t2, "MemberPattern") : s2(e3, t2);
}, Mt.VariablePattern = Rt, Mt.MemberPattern = Tt, Mt.RestElement = function(e3, t2, s2) {
  return s2(e3.argument, t2, "Pattern");
}, Mt.ArrayPattern = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.elements; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2 && s2(r2, t2, "Pattern");
  }
}, Mt.ObjectPattern = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    "Property" === r2.type ? (r2.computed && s2(r2.key, t2, "Expression"), s2(r2.value, t2, "Pattern")) : "RestElement" === r2.type && s2(r2.argument, t2, "Pattern");
  }
}, Mt.Expression = Tt, Mt.ThisExpression = Mt.Super = Mt.MetaProperty = Rt, Mt.ArrayExpression = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.elements; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2 && s2(r2, t2, "Expression");
  }
}, Mt.ObjectExpression = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, Mt.FunctionExpression = Mt.ArrowFunctionExpression = Mt.FunctionDeclaration, Mt.SequenceExpression = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.expressions; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Expression");
  }
}, Mt.TemplateLiteral = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.quasis; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
  for (var r2 = 0, o2 = e3.expressions; r2 < o2.length; r2 += 1) {
    s2(o2[r2], t2, "Expression");
  }
}, Mt.TemplateElement = Rt, Mt.UnaryExpression = Mt.UpdateExpression = function(e3, t2, s2) {
  s2(e3.argument, t2, "Expression");
}, Mt.BinaryExpression = Mt.LogicalExpression = function(e3, t2, s2) {
  s2(e3.left, t2, "Expression"), s2(e3.right, t2, "Expression");
}, Mt.AssignmentExpression = Mt.AssignmentPattern = function(e3, t2, s2) {
  s2(e3.left, t2, "Pattern"), s2(e3.right, t2, "Expression");
}, Mt.ConditionalExpression = function(e3, t2, s2) {
  s2(e3.test, t2, "Expression"), s2(e3.consequent, t2, "Expression"), s2(e3.alternate, t2, "Expression");
}, Mt.NewExpression = Mt.CallExpression = function(e3, t2, s2) {
  if (s2(e3.callee, t2, "Expression"), e3.arguments)
    for (var i2 = 0, n2 = e3.arguments; i2 < n2.length; i2 += 1) {
      s2(n2[i2], t2, "Expression");
    }
}, Mt.MemberExpression = function(e3, t2, s2) {
  s2(e3.object, t2, "Expression"), e3.computed && s2(e3.property, t2, "Expression");
}, Mt.ExportNamedDeclaration = Mt.ExportDefaultDeclaration = function(e3, t2, s2) {
  e3.declaration && s2(e3.declaration, t2, "ExportNamedDeclaration" === e3.type || e3.declaration.id ? "Statement" : "Expression"), e3.source && s2(e3.source, t2, "Expression");
}, Mt.ExportAllDeclaration = function(e3, t2, s2) {
  e3.exported && s2(e3.exported, t2), s2(e3.source, t2, "Expression");
}, Mt.ImportDeclaration = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.specifiers; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
  s2(e3.source, t2, "Expression");
}, Mt.ImportExpression = function(e3, t2, s2) {
  s2(e3.source, t2, "Expression");
}, Mt.ImportSpecifier = Mt.ImportDefaultSpecifier = Mt.ImportNamespaceSpecifier = Mt.Identifier = Mt.PrivateIdentifier = Mt.Literal = Rt, Mt.TaggedTemplateExpression = function(e3, t2, s2) {
  s2(e3.tag, t2, "Expression"), s2(e3.quasi, t2, "Expression");
}, Mt.ClassDeclaration = Mt.ClassExpression = function(e3, t2, s2) {
  return s2(e3, t2, "Class");
}, Mt.Class = function(e3, t2, s2) {
  e3.id && s2(e3.id, t2, "Pattern"), e3.superClass && s2(e3.superClass, t2, "Expression"), s2(e3.body, t2);
}, Mt.ClassBody = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.body; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, Mt.MethodDefinition = Mt.PropertyDefinition = Mt.Property = function(e3, t2, s2) {
  e3.computed && s2(e3.key, t2, "Expression"), e3.value && s2(e3.value, t2, "Expression");
};
const Ot = "ArrowFunctionExpression", Dt = "BlockStatement", Lt = "CallExpression", Vt = "ExpressionStatement", Bt = "Identifier", Ft = "Program";
let zt = "sourceMa";
zt += "ppingURL";
const jt = new RegExp(`^#[ \\f\\r\\t\\v\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+${zt}=.+`), Ut = "_rollupAnnotations", Gt = "_rollupRemoved";
function Wt(e3, t2, s2 = e3.type) {
  const { annotations: i2, code: n2 } = t2;
  let r2 = i2[t2.annotationIndex];
  for (; r2 && e3.start >= r2.end; )
    Kt(e3, r2, n2), r2 = i2[++t2.annotationIndex];
  if (r2 && r2.end <= e3.end)
    for (Mt[s2](e3, t2, Wt); (r2 = i2[t2.annotationIndex]) && r2.end <= e3.end; )
      ++t2.annotationIndex, Qt(e3, r2, false);
}
const qt = /[^\s(]/g, Ht = /\S/g;
function Kt(e3, t2, s2) {
  const i2 = [];
  let n2;
  if (Yt(s2.slice(t2.end, e3.start), qt)) {
    const t3 = e3.start;
    for (; ; ) {
      switch (i2.push(e3), e3.type) {
        case Vt:
        case "ChainExpression":
          e3 = e3.expression;
          continue;
        case "SequenceExpression":
          if (Yt(s2.slice(t3, e3.start), Ht)) {
            e3 = e3.expressions[0];
            continue;
          }
          n2 = true;
          break;
        case "ConditionalExpression":
          if (Yt(s2.slice(t3, e3.start), Ht)) {
            e3 = e3.test;
            continue;
          }
          n2 = true;
          break;
        case "LogicalExpression":
        case "BinaryExpression":
          if (Yt(s2.slice(t3, e3.start), Ht)) {
            e3 = e3.left;
            continue;
          }
          n2 = true;
          break;
        case Lt:
        case "NewExpression":
          break;
        default:
          n2 = true;
      }
      break;
    }
  } else
    n2 = true;
  if (n2)
    Qt(e3, t2, false);
  else
    for (const e4 of i2)
      Qt(e4, t2, true);
}
function Yt(e3, t2) {
  let s2;
  for (; null !== (s2 = t2.exec(e3)); ) {
    if ("/" === s2[0]) {
      const s3 = e3.charCodeAt(t2.lastIndex);
      if (42 === s3) {
        t2.lastIndex = e3.indexOf("*/", t2.lastIndex + 1) + 2;
        continue;
      }
      if (47 === s3) {
        t2.lastIndex = e3.indexOf("\n", t2.lastIndex + 1) + 1;
        continue;
      }
    }
    return t2.lastIndex = 0, false;
  }
  return true;
}
const Xt = /[#@]__PURE__/;
function Qt(e3, t2, s2) {
  const i2 = s2 ? Ut : Gt, n2 = e3[i2];
  n2 ? n2.push(t2) : e3[i2] = [t2];
}
const Jt = { ImportExpression: ["arguments"], Literal: [], Program: ["body"] };
const Zt = "variables";
class es extends ie {
  constructor(e3, t2, s2, i2 = false) {
    super(), this.deoptimized = false, this.esTreeNode = i2 ? e3 : null, this.keys = Jt[e3.type] || function(e4) {
      return Jt[e4.type] = Object.keys(e4).filter((t3) => "object" == typeof e4[t3] && 95 !== t3.charCodeAt(0)), Jt[e4.type];
    }(e3), this.parent = t2, this.context = t2.context, this.createScope(s2), this.parseNode(e3), this.initialise(), this.context.magicString.addSourcemapLocation(this.start), this.context.magicString.addSourcemapLocation(this.end);
  }
  addExportedVariables(e3, t2) {
  }
  bind() {
    for (const e3 of this.keys) {
      const t2 = this[e3];
      if (Array.isArray(t2))
        for (const e4 of t2)
          e4 == null ? void 0 : e4.bind();
      else
        t2 && t2.bind();
    }
  }
  createScope(e3) {
    this.scope = e3;
  }
  hasEffects(e3) {
    this.deoptimized || this.applyDeoptimizations();
    for (const t2 of this.keys) {
      const s2 = this[t2];
      if (null !== s2) {
        if (Array.isArray(s2)) {
          for (const t3 of s2)
            if (t3 == null ? void 0 : t3.hasEffects(e3))
              return true;
        } else if (s2.hasEffects(e3))
          return true;
      }
    }
    return false;
  }
  hasEffectsAsAssignmentTarget(e3, t2) {
    return this.hasEffects(e3) || this.hasEffectsOnInteractionAtPath(H, this.assignmentInteraction, e3);
  }
  include(e3, t2, s2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true;
    for (const s3 of this.keys) {
      const i2 = this[s3];
      if (null !== i2)
        if (Array.isArray(i2))
          for (const s4 of i2)
            s4 == null ? void 0 : s4.include(e3, t2);
        else
          i2.include(e3, t2);
    }
  }
  includeAsAssignmentTarget(e3, t2, s2) {
    this.include(e3, t2);
  }
  initialise() {
  }
  insertSemicolon(e3) {
    ";" !== e3.original[this.end - 1] && e3.appendLeft(this.end, ";");
  }
  parseNode(e3, t2) {
    for (const [s2, i2] of Object.entries(e3))
      if (!this.hasOwnProperty(s2))
        if (95 === s2.charCodeAt(0)) {
          if (s2 === Ut)
            this.annotations = i2;
          else if (s2 === Gt)
            for (const { start: e4, end: t3 } of i2)
              this.context.magicString.remove(e4, t3);
        } else if ("object" != typeof i2 || null === i2)
          this[s2] = i2;
        else if (Array.isArray(i2)) {
          this[s2] = [];
          for (const e4 of i2)
            this[s2].push(null === e4 ? null : new (this.context.getNodeConstructor(e4.type))(e4, this, this.scope, t2 == null ? void 0 : t2.includes(s2)));
        } else
          this[s2] = new (this.context.getNodeConstructor(i2.type))(i2, this, this.scope, t2 == null ? void 0 : t2.includes(s2));
  }
  render(e3, t2) {
    for (const s2 of this.keys) {
      const i2 = this[s2];
      if (null !== i2)
        if (Array.isArray(i2))
          for (const s3 of i2)
            s3 == null ? void 0 : s3.render(e3, t2);
        else
          i2.render(e3, t2);
    }
  }
  setAssignedValue(e3) {
    this.assignmentInteraction = { args: [e3], thisArg: null, type: 1 };
  }
  shouldBeIncluded(e3) {
    return this.included || !e3.brokenFlow && this.hasEffects(ft());
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e3 of this.keys) {
      const t2 = this[e3];
      if (null !== t2)
        if (Array.isArray(t2))
          for (const e4 of t2)
            e4 == null ? void 0 : e4.deoptimizePath(K);
        else
          t2.deoptimizePath(K);
    }
    this.context.requestTreeshakingPass();
  }
}
class ts extends es {
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    t2.length > 0 && this.argument.deoptimizeThisOnInteractionAtPath(e3, [U, ...t2], s2);
  }
  hasEffects(e3) {
    this.deoptimized || this.applyDeoptimizations();
    const { propertyReadSideEffects: t2 } = this.context.options.treeshake;
    return this.argument.hasEffects(e3) || t2 && ("always" === t2 || this.argument.hasEffectsOnInteractionAtPath(K, oe, e3));
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.argument.deoptimizePath([U, U]), this.context.requestTreeshakingPass();
  }
}
class ss extends ie {
  constructor(e3) {
    super(), this.description = e3;
  }
  deoptimizeThisOnInteractionAtPath({ type: e3, thisArg: t2 }, s2) {
    2 === e3 && 0 === s2.length && this.description.mutatesSelfAsArray && t2.deoptimizePath(X);
  }
  getReturnExpressionWhenCalledAtPath(e3, { thisArg: t2 }) {
    return e3.length > 0 ? re : [this.description.returnsPrimitive || ("self" === this.description.returns ? t2 || ne : this.description.returns()), false];
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    const { type: i2 } = t2;
    if (e3.length > (0 === i2 ? 1 : 0))
      return true;
    if (2 === i2) {
      const { args: e4, thisArg: i3 } = t2;
      if (true === this.description.mutatesSelfAsArray && (i3 == null ? void 0 : i3.hasEffectsOnInteractionAtPath(X, ae, s2)))
        return true;
      if (this.description.callsArgs) {
        for (const t3 of this.description.callsArgs)
          if ((_a2 = e4[t3]) == null ? void 0 : _a2.hasEffectsOnInteractionAtPath(H, he, s2))
            return true;
      }
    }
    return false;
  }
}
const is = [new ss({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: xt })], ns = [new ss({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: St })], rs = [new ss({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: Et })], os = [new ss({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: ne })], as = /^\d+$/;
class ls extends ie {
  constructor(e3, t2, s2 = false) {
    if (super(), this.prototypeExpression = t2, this.immutable = s2, this.allProperties = [], this.deoptimizedPaths = /* @__PURE__ */ Object.create(null), this.expressionsToBeDeoptimizedByKey = /* @__PURE__ */ Object.create(null), this.gettersByKey = /* @__PURE__ */ Object.create(null), this.hasLostTrack = false, this.hasUnknownDeoptimizedInteger = false, this.hasUnknownDeoptimizedProperty = false, this.propertiesAndGettersByKey = /* @__PURE__ */ Object.create(null), this.propertiesAndSettersByKey = /* @__PURE__ */ Object.create(null), this.settersByKey = /* @__PURE__ */ Object.create(null), this.thisParametersToBeDeoptimized = /* @__PURE__ */ new Set(), this.unknownIntegerProps = [], this.unmatchableGetters = [], this.unmatchablePropertiesAndGetters = [], this.unmatchableSetters = [], Array.isArray(e3))
      this.buildPropertyMaps(e3);
    else {
      this.propertiesAndGettersByKey = this.propertiesAndSettersByKey = e3;
      for (const t3 of Object.values(e3))
        this.allProperties.push(...t3);
    }
  }
  deoptimizeAllProperties(e3) {
    var _a2;
    const t2 = this.hasLostTrack || this.hasUnknownDeoptimizedProperty;
    if (e3 ? this.hasUnknownDeoptimizedProperty = true : this.hasLostTrack = true, !t2) {
      for (const e4 of [...Object.values(this.propertiesAndGettersByKey), ...Object.values(this.settersByKey)])
        for (const t3 of e4)
          t3.deoptimizePath(K);
      (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizePath([U, U]), this.deoptimizeCachedEntities();
    }
  }
  deoptimizeIntegerProperties() {
    if (!(this.hasLostTrack || this.hasUnknownDeoptimizedProperty || this.hasUnknownDeoptimizedInteger)) {
      this.hasUnknownDeoptimizedInteger = true;
      for (const [e3, t2] of Object.entries(this.propertiesAndGettersByKey))
        if (as.test(e3))
          for (const e4 of t2)
            e4.deoptimizePath(K);
      this.deoptimizeCachedIntegerEntities();
    }
  }
  deoptimizePath(e3) {
    var _a2;
    if (this.hasLostTrack || this.immutable)
      return;
    const t2 = e3[0];
    if (1 === e3.length) {
      if ("string" != typeof t2)
        return t2 === W ? this.deoptimizeIntegerProperties() : this.deoptimizeAllProperties(t2 === G);
      if (!this.deoptimizedPaths[t2]) {
        this.deoptimizedPaths[t2] = true;
        const e4 = this.expressionsToBeDeoptimizedByKey[t2];
        if (e4)
          for (const t3 of e4)
            t3.deoptimizeCache();
      }
    }
    const s2 = 1 === e3.length ? K : e3.slice(1);
    for (const e4 of "string" == typeof t2 ? [...this.propertiesAndGettersByKey[t2] || this.unmatchablePropertiesAndGetters, ...this.settersByKey[t2] || this.unmatchableSetters] : this.allProperties)
      e4.deoptimizePath(s2);
    (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizePath(1 === e3.length ? [...e3, U] : e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    const [i2, ...n2] = t2;
    if (this.hasLostTrack || (2 === e3.type || t2.length > 1) && (this.hasUnknownDeoptimizedProperty || "string" == typeof i2 && this.deoptimizedPaths[i2]))
      return void e3.thisArg.deoptimizePath(K);
    const [r2, o2, a2] = 2 === e3.type || t2.length > 1 ? [this.propertiesAndGettersByKey, this.propertiesAndGettersByKey, this.unmatchablePropertiesAndGetters] : 0 === e3.type ? [this.propertiesAndGettersByKey, this.gettersByKey, this.unmatchableGetters] : [this.propertiesAndSettersByKey, this.settersByKey, this.unmatchableSetters];
    if ("string" == typeof i2) {
      if (r2[i2]) {
        const t3 = o2[i2];
        if (t3)
          for (const i3 of t3)
            i3.deoptimizeThisOnInteractionAtPath(e3, n2, s2);
        return void (this.immutable || this.thisParametersToBeDeoptimized.add(e3.thisArg));
      }
      for (const t3 of a2)
        t3.deoptimizeThisOnInteractionAtPath(e3, n2, s2);
      if (as.test(i2))
        for (const t3 of this.unknownIntegerProps)
          t3.deoptimizeThisOnInteractionAtPath(e3, n2, s2);
    } else {
      for (const t3 of [...Object.values(o2), a2])
        for (const i3 of t3)
          i3.deoptimizeThisOnInteractionAtPath(e3, n2, s2);
      for (const t3 of this.unknownIntegerProps)
        t3.deoptimizeThisOnInteractionAtPath(e3, n2, s2);
    }
    this.immutable || this.thisParametersToBeDeoptimized.add(e3.thisArg), (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    if (0 === e3.length)
      return se;
    const i2 = e3[0], n2 = this.getMemberExpressionAndTrackDeopt(i2, s2);
    return n2 ? n2.getLiteralValueAtPath(e3.slice(1), t2, s2) : this.prototypeExpression ? this.prototypeExpression.getLiteralValueAtPath(e3, t2, s2) : 1 !== e3.length ? te : void 0;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    if (0 === e3.length)
      return re;
    const [n2, ...r2] = e3, o2 = this.getMemberExpressionAndTrackDeopt(n2, i2);
    return o2 ? o2.getReturnExpressionWhenCalledAtPath(r2, t2, s2, i2) : this.prototypeExpression ? this.prototypeExpression.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : re;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const [i2, ...n2] = e3;
    if (n2.length > 0 || 2 === t2.type) {
      const r3 = this.getMemberExpression(i2);
      return r3 ? r3.hasEffectsOnInteractionAtPath(n2, t2, s2) : !this.prototypeExpression || this.prototypeExpression.hasEffectsOnInteractionAtPath(e3, t2, s2);
    }
    if (i2 === G)
      return false;
    if (this.hasLostTrack)
      return true;
    const [r2, o2, a2] = 0 === t2.type ? [this.propertiesAndGettersByKey, this.gettersByKey, this.unmatchableGetters] : [this.propertiesAndSettersByKey, this.settersByKey, this.unmatchableSetters];
    if ("string" == typeof i2) {
      if (r2[i2]) {
        const e4 = o2[i2];
        if (e4) {
          for (const i3 of e4)
            if (i3.hasEffectsOnInteractionAtPath(n2, t2, s2))
              return true;
        }
        return false;
      }
      for (const e4 of a2)
        if (e4.hasEffectsOnInteractionAtPath(n2, t2, s2))
          return true;
    } else
      for (const e4 of [...Object.values(o2), a2])
        for (const i3 of e4)
          if (i3.hasEffectsOnInteractionAtPath(n2, t2, s2))
            return true;
    return !!this.prototypeExpression && this.prototypeExpression.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  buildPropertyMaps(e3) {
    const { allProperties: t2, propertiesAndGettersByKey: s2, propertiesAndSettersByKey: i2, settersByKey: n2, gettersByKey: r2, unknownIntegerProps: o2, unmatchablePropertiesAndGetters: a2, unmatchableGetters: l2, unmatchableSetters: h2 } = this, c2 = [];
    for (let u2 = e3.length - 1; u2 >= 0; u2--) {
      const { key: d2, kind: p2, property: f2 } = e3[u2];
      if (t2.push(f2), "string" == typeof d2)
        "set" === p2 ? i2[d2] || (i2[d2] = [f2, ...c2], n2[d2] = [f2, ...h2]) : "get" === p2 ? s2[d2] || (s2[d2] = [f2, ...a2], r2[d2] = [f2, ...l2]) : (i2[d2] || (i2[d2] = [f2, ...c2]), s2[d2] || (s2[d2] = [f2, ...a2]));
      else {
        if (d2 === W) {
          o2.push(f2);
          continue;
        }
        "set" === p2 && h2.push(f2), "get" === p2 && l2.push(f2), "get" !== p2 && c2.push(f2), "set" !== p2 && a2.push(f2);
      }
    }
  }
  deoptimizeCachedEntities() {
    for (const e3 of Object.values(this.expressionsToBeDeoptimizedByKey))
      for (const t2 of e3)
        t2.deoptimizeCache();
    for (const e3 of this.thisParametersToBeDeoptimized)
      e3.deoptimizePath(K);
  }
  deoptimizeCachedIntegerEntities() {
    for (const [e3, t2] of Object.entries(this.expressionsToBeDeoptimizedByKey))
      if (as.test(e3))
        for (const e4 of t2)
          e4.deoptimizeCache();
    for (const e3 of this.thisParametersToBeDeoptimized)
      e3.deoptimizePath(X);
  }
  getMemberExpression(e3) {
    if (this.hasLostTrack || this.hasUnknownDeoptimizedProperty || "string" != typeof e3 || this.hasUnknownDeoptimizedInteger && as.test(e3) || this.deoptimizedPaths[e3])
      return ne;
    const t2 = this.propertiesAndGettersByKey[e3];
    return 1 === (t2 == null ? void 0 : t2.length) ? t2[0] : t2 || this.unmatchablePropertiesAndGetters.length > 0 || this.unknownIntegerProps.length > 0 && as.test(e3) ? ne : null;
  }
  getMemberExpressionAndTrackDeopt(e3, t2) {
    if ("string" != typeof e3)
      return ne;
    const s2 = this.getMemberExpression(e3);
    if (s2 !== ne && !this.immutable) {
      (this.expressionsToBeDeoptimizedByKey[e3] = this.expressionsToBeDeoptimizedByKey[e3] || []).push(t2);
    }
    return s2;
  }
}
const hs = (e3) => "string" == typeof e3 && /^\d+$/.test(e3), cs = new class extends ie {
  deoptimizeThisOnInteractionAtPath({ type: e3, thisArg: t2 }, s2) {
    2 !== e3 || 1 !== s2.length || hs(s2[0]) || t2.deoptimizePath(K);
  }
  getLiteralValueAtPath(e3) {
    return 1 === e3.length && hs(e3[0]) ? void 0 : te;
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 1 || 2 === t2;
  }
}(), us = new ls({ __proto__: null, hasOwnProperty: is, isPrototypeOf: is, propertyIsEnumerable: is, toLocaleString: ns, toString: ns, valueOf: os }, cs, true), ds = [{ key: W, kind: "init", property: ne }, { key: "length", kind: "init", property: Et }], ps = [new ss({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: xt })], fs = [new ss({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: Et })], ms = [new ss({ callsArgs: null, mutatesSelfAsArray: true, returns: () => new ls(ds, As), returnsPrimitive: null })], gs = [new ss({ callsArgs: null, mutatesSelfAsArray: "deopt-only", returns: () => new ls(ds, As), returnsPrimitive: null })], ys = [new ss({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: () => new ls(ds, As), returnsPrimitive: null })], xs = [new ss({ callsArgs: null, mutatesSelfAsArray: true, returns: null, returnsPrimitive: Et })], bs = [new ss({ callsArgs: null, mutatesSelfAsArray: true, returns: null, returnsPrimitive: ne })], Es = [new ss({ callsArgs: null, mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: ne })], vs = [new ss({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: ne })], Ss = [new ss({ callsArgs: null, mutatesSelfAsArray: true, returns: "self", returnsPrimitive: null })], ks = [new ss({ callsArgs: [0], mutatesSelfAsArray: true, returns: "self", returnsPrimitive: null })], As = new ls({ __proto__: null, at: Es, concat: gs, copyWithin: Ss, entries: gs, every: ps, fill: Ss, filter: ys, find: vs, findIndex: fs, findLast: vs, findLastIndex: fs, flat: gs, flatMap: ys, forEach: vs, includes: is, indexOf: rs, join: ns, keys: os, lastIndexOf: rs, map: ys, pop: bs, push: xs, reduce: vs, reduceRight: vs, reverse: Ss, shift: bs, slice: gs, some: ps, sort: ks, splice: ms, toLocaleString: ns, toString: ns, unshift: xs, values: Es }, us, true);
class ws extends es {
  addExportedVariables(e3, t2) {
    for (const s2 of this.elements)
      s2 == null ? void 0 : s2.addExportedVariables(e3, t2);
  }
  declare(e3) {
    const t2 = [];
    for (const s2 of this.elements)
      null !== s2 && t2.push(...s2.declare(e3, ne));
    return t2;
  }
  deoptimizePath() {
    for (const e3 of this.elements)
      e3 == null ? void 0 : e3.deoptimizePath(H);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    for (const e4 of this.elements)
      if (e4 == null ? void 0 : e4.hasEffectsOnInteractionAtPath(H, t2, s2))
        return true;
    return false;
  }
  markDeclarationReached() {
    for (const e3 of this.elements)
      e3 == null ? void 0 : e3.markDeclarationReached();
  }
}
class Is extends ce {
  constructor(e3, t2, s2, i2) {
    super(e3), this.calledFromTryStatement = false, this.additionalInitializers = null, this.expressionsToBeDeoptimized = [], this.declarations = t2 ? [t2] : [], this.init = s2, this.deoptimizationTracker = i2.deoptimizationTracker, this.module = i2.module;
  }
  addDeclaration(e3, t2) {
    this.declarations.push(e3);
    const s2 = this.markInitializersForDeoptimization();
    null !== t2 && s2.push(t2);
  }
  consolidateInitializers() {
    if (null !== this.additionalInitializers) {
      for (const e3 of this.additionalInitializers)
        e3.deoptimizePath(K);
      this.additionalInitializers = null;
    }
  }
  deoptimizePath(e3) {
    var _a2, _b;
    if (!this.isReassigned && !this.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e3, this))
      if (0 === e3.length) {
        if (!this.isReassigned) {
          this.isReassigned = true;
          const e4 = this.expressionsToBeDeoptimized;
          this.expressionsToBeDeoptimized = [];
          for (const t2 of e4)
            t2.deoptimizeCache();
          (_a2 = this.init) == null ? void 0 : _a2.deoptimizePath(K);
        }
      } else
        (_b = this.init) == null ? void 0 : _b.deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    if (this.isReassigned || !this.init)
      return e3.thisArg.deoptimizePath(K);
    s2.withTrackedEntityAtPath(t2, this.init, () => this.init.deoptimizeThisOnInteractionAtPath(e3, t2, s2), void 0);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.isReassigned || !this.init ? te : t2.withTrackedEntityAtPath(e3, this.init, () => (this.expressionsToBeDeoptimized.push(s2), this.init.getLiteralValueAtPath(e3, t2, s2)), te);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.isReassigned || !this.init ? re : s2.withTrackedEntityAtPath(e3, this.init, () => (this.expressionsToBeDeoptimized.push(i2), this.init.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)), re);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return !!this.isReassigned || !(!this.init || s2.accessed.trackEntityAtPathAndGetIfTracked(e3, this) || !this.init.hasEffectsOnInteractionAtPath(e3, t2, s2));
      case 1:
        return !!this.included || 0 !== e3.length && (!!this.isReassigned || !(!this.init || s2.assigned.trackEntityAtPathAndGetIfTracked(e3, this) || !this.init.hasEffectsOnInteractionAtPath(e3, t2, s2)));
      case 2:
        return !!this.isReassigned || !(!this.init || (t2.withNew ? s2.instantiated : s2.called).trackEntityAtPathAndGetIfTracked(e3, t2.args, this) || !this.init.hasEffectsOnInteractionAtPath(e3, t2, s2));
    }
  }
  include() {
    if (!this.included) {
      this.included = true;
      for (const e3 of this.declarations) {
        e3.included || e3.include(pt(), false);
        let t2 = e3.parent;
        for (; !t2.included && (t2.included = true, t2.type !== Ft); )
          t2 = t2.parent;
      }
    }
  }
  includeCallArguments(e3, t2) {
    if (this.isReassigned || this.init && e3.includedCallArguments.has(this.init))
      for (const s2 of t2)
        s2.include(e3, false);
    else
      this.init && (e3.includedCallArguments.add(this.init), this.init.includeCallArguments(e3, t2), e3.includedCallArguments.delete(this.init));
  }
  markCalledFromTryStatement() {
    this.calledFromTryStatement = true;
  }
  markInitializersForDeoptimization() {
    return null === this.additionalInitializers && (this.additionalInitializers = null === this.init ? [] : [this.init], this.init = ne, this.isReassigned = true), this.additionalInitializers;
  }
}
function Ps(e3) {
  let t2 = "";
  do {
    const s2 = e3 % 64;
    e3 = e3 / 64 | 0, t2 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$"[s2] + t2;
  } while (0 !== e3);
  return t2;
}
function Cs(e3, t2, s2) {
  let i2 = e3, n2 = 1;
  for (; t2.has(i2) || ot.has(i2) || (s2 == null ? void 0 : s2.has(i2)); )
    i2 = `${e3}$${Ps(n2++)}`;
  return t2.add(i2), i2;
}
let $s = class {
  constructor() {
    this.children = [], this.variables = /* @__PURE__ */ new Map();
  }
  addDeclaration(e3, t2, s2, i2) {
    const n2 = e3.name;
    let r2 = this.variables.get(n2);
    return r2 ? r2.addDeclaration(e3, s2) : (r2 = new Is(e3.name, e3, s2 || gt, t2), this.variables.set(n2, r2)), r2;
  }
  contains(e3) {
    return this.variables.has(e3);
  }
  findVariable(e3) {
    throw new Error("Internal Error: findVariable needs to be implemented by a subclass");
  }
};
class Ns extends $s {
  constructor(e3) {
    super(), this.accessedOutsideVariables = /* @__PURE__ */ new Map(), this.parent = e3, e3.children.push(this);
  }
  addAccessedDynamicImport(e3) {
    (this.accessedDynamicImports || (this.accessedDynamicImports = /* @__PURE__ */ new Set())).add(e3), this.parent instanceof Ns && this.parent.addAccessedDynamicImport(e3);
  }
  addAccessedGlobals(e3, t2) {
    const s2 = t2.get(this) || /* @__PURE__ */ new Set();
    for (const t3 of e3)
      s2.add(t3);
    t2.set(this, s2), this.parent instanceof Ns && this.parent.addAccessedGlobals(e3, t2);
  }
  addNamespaceMemberAccess(e3, t2) {
    this.accessedOutsideVariables.set(e3, t2), this.parent.addNamespaceMemberAccess(e3, t2);
  }
  addReturnExpression(e3) {
    this.parent instanceof Ns && this.parent.addReturnExpression(e3);
  }
  addUsedOutsideNames(e3, t2, s2, i2) {
    for (const i3 of this.accessedOutsideVariables.values())
      i3.included && (e3.add(i3.getBaseVariableName()), "system" === t2 && s2.has(i3) && e3.add("exports"));
    const n2 = i2.get(this);
    if (n2)
      for (const t3 of n2)
        e3.add(t3);
  }
  contains(e3) {
    return this.variables.has(e3) || this.parent.contains(e3);
  }
  deconflict(e3, t2, s2) {
    const i2 = /* @__PURE__ */ new Set();
    if (this.addUsedOutsideNames(i2, e3, t2, s2), this.accessedDynamicImports)
      for (const e4 of this.accessedDynamicImports)
        e4.inlineNamespace && i2.add(e4.inlineNamespace.getBaseVariableName());
    for (const [e4, t3] of this.variables)
      (t3.included || t3.alwaysRendered) && t3.setRenderNames(null, Cs(e4, i2, t3.forbiddenNames));
    for (const i3 of this.children)
      i3.deconflict(e3, t2, s2);
  }
  findLexicalBoundary() {
    return this.parent.findLexicalBoundary();
  }
  findVariable(e3) {
    const t2 = this.variables.get(e3) || this.accessedOutsideVariables.get(e3);
    if (t2)
      return t2;
    const s2 = this.parent.findVariable(e3);
    return this.accessedOutsideVariables.set(e3, s2), s2;
  }
}
class _s extends Ns {
  constructor(e3, t2) {
    super(e3), this.parameters = [], this.hasRest = false, this.context = t2, this.hoistedBodyVarScope = new Ns(this);
  }
  addParameterDeclaration(e3) {
    const t2 = e3.name;
    let s2 = this.hoistedBodyVarScope.variables.get(t2);
    return s2 ? s2.addDeclaration(e3, null) : s2 = new Is(t2, e3, ne, this.context), this.variables.set(t2, s2), s2;
  }
  addParameterVariables(e3, t2) {
    this.parameters = e3;
    for (const t3 of e3)
      for (const e4 of t3)
        e4.alwaysRendered = true;
    this.hasRest = t2;
  }
  includeCallArguments(e3, t2) {
    let s2 = false, i2 = false;
    const n2 = this.hasRest && this.parameters[this.parameters.length - 1];
    for (const s3 of t2)
      if (s3 instanceof ts) {
        for (const s4 of t2)
          s4.include(e3, false);
        break;
      }
    for (let r2 = t2.length - 1; r2 >= 0; r2--) {
      const o2 = this.parameters[r2] || n2, a2 = t2[r2];
      if (o2)
        if (s2 = false, 0 === o2.length)
          i2 = true;
        else
          for (const e4 of o2)
            e4.included && (i2 = true), e4.calledFromTryStatement && (s2 = true);
      !i2 && a2.shouldBeIncluded(e3) && (i2 = true), i2 && a2.include(e3, s2);
    }
  }
}
class Ts extends _s {
  constructor() {
    super(...arguments), this.returnExpression = null, this.returnExpressions = [];
  }
  addReturnExpression(e3) {
    this.returnExpressions.push(e3);
  }
  getReturnExpression() {
    return null === this.returnExpression && this.updateReturnExpression(), this.returnExpression;
  }
  updateReturnExpression() {
    if (1 === this.returnExpressions.length)
      this.returnExpression = this.returnExpressions[0];
    else {
      this.returnExpression = ne;
      for (const e3 of this.returnExpressions)
        e3.deoptimizePath(K);
    }
  }
}
function Rs(e3, t2) {
  if ("MemberExpression" === e3.type)
    return !e3.computed && Rs(e3.object, e3);
  if ("Identifier" === e3.type) {
    if (!t2)
      return true;
    switch (t2.type) {
      case "MemberExpression":
        return t2.computed || e3 === t2.object;
      case "MethodDefinition":
        return t2.computed;
      case "PropertyDefinition":
      case "Property":
        return t2.computed || e3 === t2.value;
      case "ExportSpecifier":
      case "ImportSpecifier":
        return e3 === t2.local;
      case "LabeledStatement":
      case "BreakStatement":
      case "ContinueStatement":
        return false;
      default:
        return true;
    }
  }
  return false;
}
const Ms = Symbol("PureFunction"), Os = Symbol("Value Properties"), Ds = () => se, Ls = () => true, Vs = { getLiteralValue: Ds, hasEffectsWhenCalled: () => false }, Bs = { getLiteralValue: Ds, hasEffectsWhenCalled: Ls }, Fs = { __proto__: null, [Os]: Bs }, zs = { __proto__: null, [Os]: Vs }, js = { __proto__: null, [Os]: { getLiteralValue: Ds, hasEffectsWhenCalled: ({ args: e3 }, t2) => 0 === e3.length || e3[0].hasEffectsOnInteractionAtPath(Y, ae, t2) } }, Us = { __proto__: null, [Os]: Bs, prototype: Fs }, Gs = { __proto__: null, [Os]: Vs, prototype: Fs }, Ws = { __proto__: null, [Os]: Vs, from: zs, of: zs, prototype: Fs }, qs = { __proto__: null, [Os]: Vs, supportedLocalesOf: Gs }, Hs = { global: Fs, globalThis: Fs, self: Fs, window: Fs, __proto__: null, [Os]: Bs, Array: { __proto__: null, [Os]: Bs, from: Fs, isArray: zs, of: zs, prototype: Fs }, ArrayBuffer: { __proto__: null, [Os]: Vs, isView: zs, prototype: Fs }, Atomics: Fs, BigInt: Us, BigInt64Array: Us, BigUint64Array: Us, Boolean: Gs, constructor: Us, DataView: Gs, Date: { __proto__: null, [Os]: Vs, now: zs, parse: zs, prototype: Fs, UTC: zs }, decodeURI: zs, decodeURIComponent: zs, encodeURI: zs, encodeURIComponent: zs, Error: Gs, escape: zs, eval: Fs, EvalError: Gs, Float32Array: Ws, Float64Array: Ws, Function: Us, hasOwnProperty: Fs, Infinity: Fs, Int16Array: Ws, Int32Array: Ws, Int8Array: Ws, isFinite: zs, isNaN: zs, isPrototypeOf: Fs, JSON: Fs, Map: Gs, Math: { __proto__: null, [Os]: Bs, abs: zs, acos: zs, acosh: zs, asin: zs, asinh: zs, atan: zs, atan2: zs, atanh: zs, cbrt: zs, ceil: zs, clz32: zs, cos: zs, cosh: zs, exp: zs, expm1: zs, floor: zs, fround: zs, hypot: zs, imul: zs, log: zs, log10: zs, log1p: zs, log2: zs, max: zs, min: zs, pow: zs, random: zs, round: zs, sign: zs, sin: zs, sinh: zs, sqrt: zs, tan: zs, tanh: zs, trunc: zs }, NaN: Fs, Number: { __proto__: null, [Os]: Vs, isFinite: zs, isInteger: zs, isNaN: zs, isSafeInteger: zs, parseFloat: zs, parseInt: zs, prototype: Fs }, Object: { __proto__: null, [Os]: Vs, create: zs, defineProperty: js, defineProperties: js, freeze: js, getOwnPropertyDescriptor: zs, getOwnPropertyDescriptors: zs, getOwnPropertyNames: zs, getOwnPropertySymbols: zs, getPrototypeOf: zs, hasOwn: zs, is: zs, isExtensible: zs, isFrozen: zs, isSealed: zs, keys: zs, fromEntries: zs, entries: zs, prototype: Fs }, parseFloat: zs, parseInt: zs, Promise: { __proto__: null, [Os]: Bs, all: Fs, allSettled: Fs, any: Fs, prototype: Fs, race: Fs, reject: Fs, resolve: Fs }, propertyIsEnumerable: Fs, Proxy: Fs, RangeError: Gs, ReferenceError: Gs, Reflect: Fs, RegExp: Gs, Set: Gs, SharedArrayBuffer: Us, String: { __proto__: null, [Os]: Vs, fromCharCode: zs, fromCodePoint: zs, prototype: Fs, raw: zs }, Symbol: { __proto__: null, [Os]: Vs, for: zs, keyFor: zs, prototype: Fs, toStringTag: { __proto__: null, [Os]: { getLiteralValue: () => q, hasEffectsWhenCalled: Ls } } }, SyntaxError: Gs, toLocaleString: Fs, toString: Fs, TypeError: Gs, Uint16Array: Ws, Uint32Array: Ws, Uint8Array: Ws, Uint8ClampedArray: Ws, unescape: zs, URIError: Gs, valueOf: Fs, WeakMap: Gs, WeakSet: Gs, clearInterval: Us, clearTimeout: Us, console: Fs, Intl: { __proto__: null, [Os]: Bs, Collator: qs, DateTimeFormat: qs, ListFormat: qs, NumberFormat: qs, PluralRules: qs, RelativeTimeFormat: qs }, setInterval: Us, setTimeout: Us, TextDecoder: Us, TextEncoder: Us, URL: Us, URLSearchParams: Us, AbortController: Us, AbortSignal: Us, addEventListener: Fs, alert: Fs, AnalyserNode: Us, Animation: Us, AnimationEvent: Us, applicationCache: Fs, ApplicationCache: Us, ApplicationCacheErrorEvent: Us, atob: Fs, Attr: Us, Audio: Us, AudioBuffer: Us, AudioBufferSourceNode: Us, AudioContext: Us, AudioDestinationNode: Us, AudioListener: Us, AudioNode: Us, AudioParam: Us, AudioProcessingEvent: Us, AudioScheduledSourceNode: Us, AudioWorkletNode: Us, BarProp: Us, BaseAudioContext: Us, BatteryManager: Us, BeforeUnloadEvent: Us, BiquadFilterNode: Us, Blob: Us, BlobEvent: Us, blur: Fs, BroadcastChannel: Us, btoa: Fs, ByteLengthQueuingStrategy: Us, Cache: Us, caches: Fs, CacheStorage: Us, cancelAnimationFrame: Fs, cancelIdleCallback: Fs, CanvasCaptureMediaStreamTrack: Us, CanvasGradient: Us, CanvasPattern: Us, CanvasRenderingContext2D: Us, ChannelMergerNode: Us, ChannelSplitterNode: Us, CharacterData: Us, clientInformation: Fs, ClipboardEvent: Us, close: Fs, closed: Fs, CloseEvent: Us, Comment: Us, CompositionEvent: Us, confirm: Fs, ConstantSourceNode: Us, ConvolverNode: Us, CountQueuingStrategy: Us, createImageBitmap: Fs, Credential: Us, CredentialsContainer: Us, crypto: Fs, Crypto: Us, CryptoKey: Us, CSS: Us, CSSConditionRule: Us, CSSFontFaceRule: Us, CSSGroupingRule: Us, CSSImportRule: Us, CSSKeyframeRule: Us, CSSKeyframesRule: Us, CSSMediaRule: Us, CSSNamespaceRule: Us, CSSPageRule: Us, CSSRule: Us, CSSRuleList: Us, CSSStyleDeclaration: Us, CSSStyleRule: Us, CSSStyleSheet: Us, CSSSupportsRule: Us, CustomElementRegistry: Us, customElements: Fs, CustomEvent: Us, DataTransfer: Us, DataTransferItem: Us, DataTransferItemList: Us, defaultstatus: Fs, defaultStatus: Fs, DelayNode: Us, DeviceMotionEvent: Us, DeviceOrientationEvent: Us, devicePixelRatio: Fs, dispatchEvent: Fs, document: Fs, Document: Us, DocumentFragment: Us, DocumentType: Us, DOMError: Us, DOMException: Us, DOMImplementation: Us, DOMMatrix: Us, DOMMatrixReadOnly: Us, DOMParser: Us, DOMPoint: Us, DOMPointReadOnly: Us, DOMQuad: Us, DOMRect: Us, DOMRectReadOnly: Us, DOMStringList: Us, DOMStringMap: Us, DOMTokenList: Us, DragEvent: Us, DynamicsCompressorNode: Us, Element: Us, ErrorEvent: Us, Event: Us, EventSource: Us, EventTarget: Us, external: Fs, fetch: Fs, File: Us, FileList: Us, FileReader: Us, find: Fs, focus: Fs, FocusEvent: Us, FontFace: Us, FontFaceSetLoadEvent: Us, FormData: Us, frames: Fs, GainNode: Us, Gamepad: Us, GamepadButton: Us, GamepadEvent: Us, getComputedStyle: Fs, getSelection: Fs, HashChangeEvent: Us, Headers: Us, history: Fs, History: Us, HTMLAllCollection: Us, HTMLAnchorElement: Us, HTMLAreaElement: Us, HTMLAudioElement: Us, HTMLBaseElement: Us, HTMLBodyElement: Us, HTMLBRElement: Us, HTMLButtonElement: Us, HTMLCanvasElement: Us, HTMLCollection: Us, HTMLContentElement: Us, HTMLDataElement: Us, HTMLDataListElement: Us, HTMLDetailsElement: Us, HTMLDialogElement: Us, HTMLDirectoryElement: Us, HTMLDivElement: Us, HTMLDListElement: Us, HTMLDocument: Us, HTMLElement: Us, HTMLEmbedElement: Us, HTMLFieldSetElement: Us, HTMLFontElement: Us, HTMLFormControlsCollection: Us, HTMLFormElement: Us, HTMLFrameElement: Us, HTMLFrameSetElement: Us, HTMLHeadElement: Us, HTMLHeadingElement: Us, HTMLHRElement: Us, HTMLHtmlElement: Us, HTMLIFrameElement: Us, HTMLImageElement: Us, HTMLInputElement: Us, HTMLLabelElement: Us, HTMLLegendElement: Us, HTMLLIElement: Us, HTMLLinkElement: Us, HTMLMapElement: Us, HTMLMarqueeElement: Us, HTMLMediaElement: Us, HTMLMenuElement: Us, HTMLMetaElement: Us, HTMLMeterElement: Us, HTMLModElement: Us, HTMLObjectElement: Us, HTMLOListElement: Us, HTMLOptGroupElement: Us, HTMLOptionElement: Us, HTMLOptionsCollection: Us, HTMLOutputElement: Us, HTMLParagraphElement: Us, HTMLParamElement: Us, HTMLPictureElement: Us, HTMLPreElement: Us, HTMLProgressElement: Us, HTMLQuoteElement: Us, HTMLScriptElement: Us, HTMLSelectElement: Us, HTMLShadowElement: Us, HTMLSlotElement: Us, HTMLSourceElement: Us, HTMLSpanElement: Us, HTMLStyleElement: Us, HTMLTableCaptionElement: Us, HTMLTableCellElement: Us, HTMLTableColElement: Us, HTMLTableElement: Us, HTMLTableRowElement: Us, HTMLTableSectionElement: Us, HTMLTemplateElement: Us, HTMLTextAreaElement: Us, HTMLTimeElement: Us, HTMLTitleElement: Us, HTMLTrackElement: Us, HTMLUListElement: Us, HTMLUnknownElement: Us, HTMLVideoElement: Us, IDBCursor: Us, IDBCursorWithValue: Us, IDBDatabase: Us, IDBFactory: Us, IDBIndex: Us, IDBKeyRange: Us, IDBObjectStore: Us, IDBOpenDBRequest: Us, IDBRequest: Us, IDBTransaction: Us, IDBVersionChangeEvent: Us, IdleDeadline: Us, IIRFilterNode: Us, Image: Us, ImageBitmap: Us, ImageBitmapRenderingContext: Us, ImageCapture: Us, ImageData: Us, indexedDB: Fs, innerHeight: Fs, innerWidth: Fs, InputEvent: Us, IntersectionObserver: Us, IntersectionObserverEntry: Us, isSecureContext: Fs, KeyboardEvent: Us, KeyframeEffect: Us, length: Fs, localStorage: Fs, location: Fs, Location: Us, locationbar: Fs, matchMedia: Fs, MediaDeviceInfo: Us, MediaDevices: Us, MediaElementAudioSourceNode: Us, MediaEncryptedEvent: Us, MediaError: Us, MediaKeyMessageEvent: Us, MediaKeySession: Us, MediaKeyStatusMap: Us, MediaKeySystemAccess: Us, MediaList: Us, MediaQueryList: Us, MediaQueryListEvent: Us, MediaRecorder: Us, MediaSettingsRange: Us, MediaSource: Us, MediaStream: Us, MediaStreamAudioDestinationNode: Us, MediaStreamAudioSourceNode: Us, MediaStreamEvent: Us, MediaStreamTrack: Us, MediaStreamTrackEvent: Us, menubar: Fs, MessageChannel: Us, MessageEvent: Us, MessagePort: Us, MIDIAccess: Us, MIDIConnectionEvent: Us, MIDIInput: Us, MIDIInputMap: Us, MIDIMessageEvent: Us, MIDIOutput: Us, MIDIOutputMap: Us, MIDIPort: Us, MimeType: Us, MimeTypeArray: Us, MouseEvent: Us, moveBy: Fs, moveTo: Fs, MutationEvent: Us, MutationObserver: Us, MutationRecord: Us, name: Fs, NamedNodeMap: Us, NavigationPreloadManager: Us, navigator: Fs, Navigator: Us, NetworkInformation: Us, Node: Us, NodeFilter: Fs, NodeIterator: Us, NodeList: Us, Notification: Us, OfflineAudioCompletionEvent: Us, OfflineAudioContext: Us, offscreenBuffering: Fs, OffscreenCanvas: Us, open: Fs, openDatabase: Fs, Option: Us, origin: Fs, OscillatorNode: Us, outerHeight: Fs, outerWidth: Fs, PageTransitionEvent: Us, pageXOffset: Fs, pageYOffset: Fs, PannerNode: Us, parent: Fs, Path2D: Us, PaymentAddress: Us, PaymentRequest: Us, PaymentRequestUpdateEvent: Us, PaymentResponse: Us, performance: Fs, Performance: Us, PerformanceEntry: Us, PerformanceLongTaskTiming: Us, PerformanceMark: Us, PerformanceMeasure: Us, PerformanceNavigation: Us, PerformanceNavigationTiming: Us, PerformanceObserver: Us, PerformanceObserverEntryList: Us, PerformancePaintTiming: Us, PerformanceResourceTiming: Us, PerformanceTiming: Us, PeriodicWave: Us, Permissions: Us, PermissionStatus: Us, personalbar: Fs, PhotoCapabilities: Us, Plugin: Us, PluginArray: Us, PointerEvent: Us, PopStateEvent: Us, postMessage: Fs, Presentation: Us, PresentationAvailability: Us, PresentationConnection: Us, PresentationConnectionAvailableEvent: Us, PresentationConnectionCloseEvent: Us, PresentationConnectionList: Us, PresentationReceiver: Us, PresentationRequest: Us, print: Fs, ProcessingInstruction: Us, ProgressEvent: Us, PromiseRejectionEvent: Us, prompt: Fs, PushManager: Us, PushSubscription: Us, PushSubscriptionOptions: Us, queueMicrotask: Fs, RadioNodeList: Us, Range: Us, ReadableStream: Us, RemotePlayback: Us, removeEventListener: Fs, Request: Us, requestAnimationFrame: Fs, requestIdleCallback: Fs, resizeBy: Fs, ResizeObserver: Us, ResizeObserverEntry: Us, resizeTo: Fs, Response: Us, RTCCertificate: Us, RTCDataChannel: Us, RTCDataChannelEvent: Us, RTCDtlsTransport: Us, RTCIceCandidate: Us, RTCIceTransport: Us, RTCPeerConnection: Us, RTCPeerConnectionIceEvent: Us, RTCRtpReceiver: Us, RTCRtpSender: Us, RTCSctpTransport: Us, RTCSessionDescription: Us, RTCStatsReport: Us, RTCTrackEvent: Us, screen: Fs, Screen: Us, screenLeft: Fs, ScreenOrientation: Us, screenTop: Fs, screenX: Fs, screenY: Fs, ScriptProcessorNode: Us, scroll: Fs, scrollbars: Fs, scrollBy: Fs, scrollTo: Fs, scrollX: Fs, scrollY: Fs, SecurityPolicyViolationEvent: Us, Selection: Us, ServiceWorker: Us, ServiceWorkerContainer: Us, ServiceWorkerRegistration: Us, sessionStorage: Fs, ShadowRoot: Us, SharedWorker: Us, SourceBuffer: Us, SourceBufferList: Us, speechSynthesis: Fs, SpeechSynthesisEvent: Us, SpeechSynthesisUtterance: Us, StaticRange: Us, status: Fs, statusbar: Fs, StereoPannerNode: Us, stop: Fs, Storage: Us, StorageEvent: Us, StorageManager: Us, styleMedia: Fs, StyleSheet: Us, StyleSheetList: Us, SubtleCrypto: Us, SVGAElement: Us, SVGAngle: Us, SVGAnimatedAngle: Us, SVGAnimatedBoolean: Us, SVGAnimatedEnumeration: Us, SVGAnimatedInteger: Us, SVGAnimatedLength: Us, SVGAnimatedLengthList: Us, SVGAnimatedNumber: Us, SVGAnimatedNumberList: Us, SVGAnimatedPreserveAspectRatio: Us, SVGAnimatedRect: Us, SVGAnimatedString: Us, SVGAnimatedTransformList: Us, SVGAnimateElement: Us, SVGAnimateMotionElement: Us, SVGAnimateTransformElement: Us, SVGAnimationElement: Us, SVGCircleElement: Us, SVGClipPathElement: Us, SVGComponentTransferFunctionElement: Us, SVGDefsElement: Us, SVGDescElement: Us, SVGDiscardElement: Us, SVGElement: Us, SVGEllipseElement: Us, SVGFEBlendElement: Us, SVGFEColorMatrixElement: Us, SVGFEComponentTransferElement: Us, SVGFECompositeElement: Us, SVGFEConvolveMatrixElement: Us, SVGFEDiffuseLightingElement: Us, SVGFEDisplacementMapElement: Us, SVGFEDistantLightElement: Us, SVGFEDropShadowElement: Us, SVGFEFloodElement: Us, SVGFEFuncAElement: Us, SVGFEFuncBElement: Us, SVGFEFuncGElement: Us, SVGFEFuncRElement: Us, SVGFEGaussianBlurElement: Us, SVGFEImageElement: Us, SVGFEMergeElement: Us, SVGFEMergeNodeElement: Us, SVGFEMorphologyElement: Us, SVGFEOffsetElement: Us, SVGFEPointLightElement: Us, SVGFESpecularLightingElement: Us, SVGFESpotLightElement: Us, SVGFETileElement: Us, SVGFETurbulenceElement: Us, SVGFilterElement: Us, SVGForeignObjectElement: Us, SVGGElement: Us, SVGGeometryElement: Us, SVGGradientElement: Us, SVGGraphicsElement: Us, SVGImageElement: Us, SVGLength: Us, SVGLengthList: Us, SVGLinearGradientElement: Us, SVGLineElement: Us, SVGMarkerElement: Us, SVGMaskElement: Us, SVGMatrix: Us, SVGMetadataElement: Us, SVGMPathElement: Us, SVGNumber: Us, SVGNumberList: Us, SVGPathElement: Us, SVGPatternElement: Us, SVGPoint: Us, SVGPointList: Us, SVGPolygonElement: Us, SVGPolylineElement: Us, SVGPreserveAspectRatio: Us, SVGRadialGradientElement: Us, SVGRect: Us, SVGRectElement: Us, SVGScriptElement: Us, SVGSetElement: Us, SVGStopElement: Us, SVGStringList: Us, SVGStyleElement: Us, SVGSVGElement: Us, SVGSwitchElement: Us, SVGSymbolElement: Us, SVGTextContentElement: Us, SVGTextElement: Us, SVGTextPathElement: Us, SVGTextPositioningElement: Us, SVGTitleElement: Us, SVGTransform: Us, SVGTransformList: Us, SVGTSpanElement: Us, SVGUnitTypes: Us, SVGUseElement: Us, SVGViewElement: Us, TaskAttributionTiming: Us, Text: Us, TextEvent: Us, TextMetrics: Us, TextTrack: Us, TextTrackCue: Us, TextTrackCueList: Us, TextTrackList: Us, TimeRanges: Us, toolbar: Fs, top: Fs, Touch: Us, TouchEvent: Us, TouchList: Us, TrackEvent: Us, TransitionEvent: Us, TreeWalker: Us, UIEvent: Us, ValidityState: Us, visualViewport: Fs, VisualViewport: Us, VTTCue: Us, WaveShaperNode: Us, WebAssembly: Fs, WebGL2RenderingContext: Us, WebGLActiveInfo: Us, WebGLBuffer: Us, WebGLContextEvent: Us, WebGLFramebuffer: Us, WebGLProgram: Us, WebGLQuery: Us, WebGLRenderbuffer: Us, WebGLRenderingContext: Us, WebGLSampler: Us, WebGLShader: Us, WebGLShaderPrecisionFormat: Us, WebGLSync: Us, WebGLTexture: Us, WebGLTransformFeedback: Us, WebGLUniformLocation: Us, WebGLVertexArrayObject: Us, WebSocket: Us, WheelEvent: Us, Window: Us, Worker: Us, WritableStream: Us, XMLDocument: Us, XMLHttpRequest: Us, XMLHttpRequestEventTarget: Us, XMLHttpRequestUpload: Us, XMLSerializer: Us, XPathEvaluator: Us, XPathExpression: Us, XPathResult: Us, XSLTProcessor: Us };
for (const e3 of ["window", "global", "self", "globalThis"])
  Hs[e3] = Hs;
function Ks(e3) {
  let t2 = Hs;
  for (const s2 of e3) {
    if ("string" != typeof s2)
      return null;
    if (t2 = t2[s2], !t2)
      return null;
  }
  return t2[Os];
}
class Ys extends ce {
  constructor() {
    super(...arguments), this.isReassigned = true;
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const i2 = Ks([this.name, ...e3]);
    return i2 ? i2.getLiteralValue() : te;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return 0 === e3.length ? "undefined" !== this.name && !Ks([this.name]) : !Ks([this.name, ...e3].slice(0, -1));
      case 1:
        return true;
      case 2: {
        const i2 = Ks([this.name, ...e3]);
        return !i2 || i2.hasEffectsWhenCalled(t2, s2);
      }
    }
  }
}
const Xs = { __proto__: null, class: true, const: true, let: true, var: true };
class Qs extends es {
  constructor() {
    super(...arguments), this.variable = null, this.isTDZAccess = null;
  }
  addExportedVariables(e3, t2) {
    t2.has(this.variable) && e3.push(this.variable);
  }
  bind() {
    !this.variable && Rs(this, this.parent) && (this.variable = this.scope.findVariable(this.name), this.variable.addReference(this));
  }
  declare(e3, t2) {
    let s2;
    const { treeshake: i2 } = this.context.options;
    switch (e3) {
      case "var":
        s2 = this.scope.addDeclaration(this, this.context, t2, true), i2 && i2.correctVarValueBeforeDeclaration && s2.markInitializersForDeoptimization();
        break;
      case "function":
      case "let":
      case "const":
      case "class":
        s2 = this.scope.addDeclaration(this, this.context, t2, false);
        break;
      case "parameter":
        s2 = this.scope.addParameterDeclaration(this);
        break;
      default:
        throw new Error(`Internal Error: Unexpected identifier kind ${e3}.`);
    }
    return s2.kind = e3, [this.variable = s2];
  }
  deoptimizePath(e3) {
    var _a2;
    0 !== e3.length || this.scope.contains(this.name) || this.disallowImportReassignment(), (_a2 = this.variable) == null ? void 0 : _a2.deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.variable.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getVariableRespectingTDZ().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const [n2, r2] = this.getVariableRespectingTDZ().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
    return [n2, r2 || this.isPureFunction(e3)];
  }
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), !(!this.isPossibleTDZ() || "var" === this.variable.kind) || this.context.options.treeshake.unknownGlobalSideEffects && this.variable instanceof Ys && !this.isPureFunction(H) && this.variable.hasEffectsOnInteractionAtPath(H, oe, e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return null !== this.variable && !this.isPureFunction(e3) && this.getVariableRespectingTDZ().hasEffectsOnInteractionAtPath(e3, t2, s2);
      case 1:
        return (e3.length > 0 ? this.getVariableRespectingTDZ() : this.variable).hasEffectsOnInteractionAtPath(e3, t2, s2);
      case 2:
        return !this.isPureFunction(e3) && this.getVariableRespectingTDZ().hasEffectsOnInteractionAtPath(e3, t2, s2);
    }
  }
  include() {
    this.deoptimized || this.applyDeoptimizations(), this.included || (this.included = true, null !== this.variable && this.context.includeVariableInModule(this.variable));
  }
  includeCallArguments(e3, t2) {
    this.variable.includeCallArguments(e3, t2);
  }
  isPossibleTDZ() {
    if (null !== this.isTDZAccess)
      return this.isTDZAccess;
    if (!(this.variable instanceof Is && this.variable.kind && this.variable.kind in Xs && this.variable.module === this.context.module))
      return this.isTDZAccess = false;
    let e3;
    return this.variable.declarations && 1 === this.variable.declarations.length && (e3 = this.variable.declarations[0]) && this.start < e3.start && Js(this) === Js(e3) ? this.isTDZAccess = true : this.variable.initReached ? this.isTDZAccess = false : this.isTDZAccess = true;
  }
  markDeclarationReached() {
    this.variable.initReached = true;
  }
  render(e3, { snippets: { getPropertyAccess: t2 }, useOriginalName: s2 }, { renderedParentType: i2, isCalleeOfRenderedParent: n2, isShorthandProperty: r2 } = de) {
    if (this.variable) {
      const o2 = this.variable.getName(t2, s2);
      o2 !== this.name && (e3.overwrite(this.start, this.end, o2, { contentOnly: true, storeName: true }), r2 && e3.prependRight(this.start, `${this.name}: `)), "eval" === o2 && i2 === Lt && n2 && e3.appendRight(this.start, "0, ");
    }
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.variable instanceof Is && (this.variable.consolidateInitializers(), this.context.requestTreeshakingPass());
  }
  disallowImportReassignment() {
    return this.context.error(qe(this.name, this.context.module.id), this.start);
  }
  getVariableRespectingTDZ() {
    return this.isPossibleTDZ() ? ne : this.variable;
  }
  isPureFunction(e3) {
    let t2 = this.context.manualPureFunctions[this.name];
    for (const s2 of e3) {
      if (!t2)
        return false;
      if (t2[Ms])
        return true;
      t2 = t2[s2];
    }
    return t2 == null ? void 0 : t2[Ms];
  }
}
function Js(e3) {
  for (; e3 && !/^Program|Function/.test(e3.type); )
    e3 = e3.parent;
  return e3;
}
function Zs(e3, t2, s2, i2) {
  if (t2.remove(s2, i2), e3.annotations)
    for (const i3 of e3.annotations) {
      if (!(i3.start < s2))
        return;
      t2.remove(i3.start, i3.end);
    }
}
function ei(e3, t2) {
  if (e3.annotations || e3.parent.type !== Vt || (e3 = e3.parent), e3.annotations)
    for (const s2 of e3.annotations)
      t2.remove(s2.start, s2.end);
}
const ti = { isNoStatement: true };
function si(e3, t2, s2 = 0) {
  let i2, n2;
  for (i2 = e3.indexOf(t2, s2); ; ) {
    if (-1 === (s2 = e3.indexOf("/", s2)) || s2 >= i2)
      return i2;
    n2 = e3.charCodeAt(++s2), ++s2, (s2 = 47 === n2 ? e3.indexOf("\n", s2) + 1 : e3.indexOf("*/", s2) + 2) > i2 && (i2 = e3.indexOf(t2, s2));
  }
}
const ii = /\S/g;
function ni(e3, t2) {
  ii.lastIndex = t2;
  return ii.exec(e3).index;
}
function ri(e3) {
  let t2, s2, i2 = 0;
  for (t2 = e3.indexOf("\n", i2); ; ) {
    if (i2 = e3.indexOf("/", i2), -1 === i2 || i2 > t2)
      return [t2, t2 + 1];
    if (s2 = e3.charCodeAt(i2 + 1), 47 === s2)
      return [i2, t2 + 1];
    i2 = e3.indexOf("*/", i2 + 3) + 2, i2 > t2 && (t2 = e3.indexOf("\n", i2));
  }
}
function oi(e3, t2, s2, i2, n2) {
  let r2, o2, a2, l2, h2 = e3[0], c2 = !h2.included || h2.needsBoundaries;
  c2 && (l2 = s2 + ri(t2.original.slice(s2, h2.start))[1]);
  for (let s3 = 1; s3 <= e3.length; s3++)
    r2 = h2, o2 = l2, a2 = c2, h2 = e3[s3], c2 = void 0 !== h2 && (!h2.included || h2.needsBoundaries), a2 || c2 ? (l2 = r2.end + ri(t2.original.slice(r2.end, void 0 === h2 ? i2 : h2.start))[1], r2.included ? a2 ? r2.render(t2, n2, { end: l2, start: o2 }) : r2.render(t2, n2) : Zs(r2, t2, o2, l2)) : r2.render(t2, n2);
}
function ai(e3, t2, s2, i2) {
  const n2 = [];
  let r2, o2, a2, l2, h2 = s2 - 1;
  for (const i3 of e3) {
    for (void 0 !== r2 && (h2 = r2.end + si(t2.original.slice(r2.end, i3.start), ",")), o2 = a2 = h2 + 1 + ri(t2.original.slice(h2 + 1, i3.start))[1]; l2 = t2.original.charCodeAt(o2), 32 === l2 || 9 === l2 || 10 === l2 || 13 === l2; )
      o2++;
    void 0 !== r2 && n2.push({ contentEnd: a2, end: o2, node: r2, separator: h2, start: s2 }), r2 = i3, s2 = o2;
  }
  return n2.push({ contentEnd: i2, end: i2, node: r2, separator: null, start: s2 }), n2;
}
function li(e3, t2, s2) {
  for (; ; ) {
    const [i2, n2] = ri(e3.original.slice(t2, s2));
    if (-1 === i2)
      break;
    e3.remove(t2 + i2, t2 += n2);
  }
}
class hi extends Ns {
  addDeclaration(e3, t2, s2, i2) {
    if (i2) {
      const n2 = this.parent.addDeclaration(e3, t2, s2, i2);
      return n2.markInitializersForDeoptimization(), n2;
    }
    return super.addDeclaration(e3, t2, s2, false);
  }
}
class ci extends es {
  initialise() {
    var e3, t2;
    this.directive && "use strict" !== this.directive && this.parent.type === Ft && this.context.warn((e3 = this.directive, { code: "MODULE_LEVEL_DIRECTIVE", id: t2 = this.context.module.id, message: `Module level directives cause errors when bundled, "${e3}" in "${O(t2)}" was ignored.` }), this.start);
  }
  render(e3, t2) {
    super.render(e3, t2), this.included && this.insertSemicolon(e3);
  }
  shouldBeIncluded(e3) {
    return this.directive && "use strict" !== this.directive ? this.parent.type !== Ft : super.shouldBeIncluded(e3);
  }
  applyDeoptimizations() {
  }
}
class ui extends es {
  constructor() {
    super(...arguments), this.directlyIncluded = false;
  }
  addImplicitReturnExpressionToScope() {
    const e3 = this.body[this.body.length - 1];
    e3 && "ReturnStatement" === e3.type || this.scope.addReturnExpression(ne);
  }
  createScope(e3) {
    this.scope = this.parent.preventChildBlockScope ? e3 : new hi(e3);
  }
  hasEffects(e3) {
    if (this.deoptimizeBody)
      return true;
    for (const t2 of this.body) {
      if (e3.brokenFlow)
        break;
      if (t2.hasEffects(e3))
        return true;
    }
    return false;
  }
  include(e3, t2) {
    if (!this.deoptimizeBody || !this.directlyIncluded) {
      this.included = true, this.directlyIncluded = true, this.deoptimizeBody && (t2 = true);
      for (const s2 of this.body)
        (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
    }
  }
  initialise() {
    const e3 = this.body[0];
    this.deoptimizeBody = e3 instanceof ci && "use asm" === e3.directive;
  }
  render(e3, t2) {
    this.body.length > 0 ? oi(this.body, e3, this.start + 1, this.end - 1, t2) : super.render(e3, t2);
  }
}
class di extends es {
  constructor() {
    super(...arguments), this.declarationInit = null;
  }
  addExportedVariables(e3, t2) {
    this.argument.addExportedVariables(e3, t2);
  }
  declare(e3, t2) {
    return this.declarationInit = t2, this.argument.declare(e3, ne);
  }
  deoptimizePath(e3) {
    0 === e3.length && this.argument.deoptimizePath(H);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return e3.length > 0 || this.argument.hasEffectsOnInteractionAtPath(H, t2, s2);
  }
  markDeclarationReached() {
    this.argument.markDeclarationReached();
  }
  applyDeoptimizations() {
    this.deoptimized = true, null !== this.declarationInit && (this.declarationInit.deoptimizePath([U, U]), this.context.requestTreeshakingPass());
  }
}
class pi extends es {
  constructor() {
    super(...arguments), this.objectEntity = null, this.deoptimizedReturn = false;
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3), 1 === e3.length && e3[0] === U && this.scope.getReturnExpression().deoptimizePath(K);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    t2.length > 0 && this.getObjectEntity().deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return e3.length > 0 ? this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : this.async ? (this.deoptimizedReturn || (this.deoptimizedReturn = true, this.scope.getReturnExpression().deoptimizePath(K), this.context.requestTreeshakingPass()), re) : [this.scope.getReturnExpression(), false];
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    if (e3.length > 0 || 2 !== t2.type)
      return this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
    if (this.async) {
      const { propertyReadSideEffects: e4 } = this.context.options.treeshake, t3 = this.scope.getReturnExpression();
      if (t3.hasEffectsOnInteractionAtPath(["then"], he, s2) || e4 && ("always" === e4 || t3.hasEffectsOnInteractionAtPath(["then"], oe, s2)))
        return true;
    }
    for (const e4 of this.params)
      if (e4.hasEffects(s2))
        return true;
    return false;
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true;
    const { brokenFlow: s2 } = e3;
    e3.brokenFlow = 0, this.body.include(e3, t2), e3.brokenFlow = s2;
  }
  includeCallArguments(e3, t2) {
    this.scope.includeCallArguments(e3, t2);
  }
  initialise() {
    this.scope.addParameterVariables(this.params.map((e3) => e3.declare("parameter", ne)), this.params[this.params.length - 1] instanceof di), this.body instanceof ui ? this.body.addImplicitReturnExpressionToScope() : this.scope.addReturnExpression(this.body);
  }
  parseNode(e3) {
    e3.body.type === Dt && (this.body = new ui(e3.body, this, this.scope.hoistedBodyVarScope)), super.parseNode(e3);
  }
  applyDeoptimizations() {
  }
}
pi.prototype.preventChildBlockScope = true;
class fi extends pi {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e3) {
    this.scope = new Ts(e3, this.context);
  }
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), false;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    if (super.hasEffectsOnInteractionAtPath(e3, t2, s2))
      return true;
    if (2 === t2.type) {
      const { ignore: e4, brokenFlow: t3 } = s2;
      if (s2.ignore = { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: true, this: false }, this.body.hasEffects(s2))
        return true;
      s2.ignore = e4, s2.brokenFlow = t3;
    }
    return false;
  }
  include(e3, t2) {
    super.include(e3, t2);
    for (const s2 of this.params)
      s2 instanceof Qs || s2.include(e3, t2);
  }
  getObjectEntity() {
    return null !== this.objectEntity ? this.objectEntity : this.objectEntity = new ls([], us);
  }
}
function mi(e3, { exportNamesByVariable: t2, snippets: { _: s2, getObject: i2, getPropertyAccess: n2 } }, r2 = "") {
  if (1 === e3.length && 1 === t2.get(e3[0]).length) {
    const i3 = e3[0];
    return `exports('${t2.get(i3)}',${s2}${i3.getName(n2)}${r2})`;
  }
  {
    const s3 = [];
    for (const i3 of e3)
      for (const e4 of t2.get(i3))
        s3.push([e4, i3.getName(n2) + r2]);
    return `exports(${i2(s3, { lineBreakIndent: null })})`;
  }
}
function gi(e3, t2, s2, i2, { exportNamesByVariable: n2, snippets: { _: r2 } }) {
  i2.prependRight(t2, `exports('${n2.get(e3)}',${r2}`), i2.appendLeft(s2, ")");
}
function yi(e3, t2, s2, i2, n2, r2) {
  const { _: o2, getPropertyAccess: a2 } = r2.snippets;
  n2.appendLeft(s2, `,${o2}${mi([e3], r2)},${o2}${e3.getName(a2)}`), i2 && (n2.prependRight(t2, "("), n2.appendLeft(s2, ")"));
}
class xi extends es {
  addExportedVariables(e3, t2) {
    for (const s2 of this.properties)
      "Property" === s2.type ? s2.value.addExportedVariables(e3, t2) : s2.argument.addExportedVariables(e3, t2);
  }
  declare(e3, t2) {
    const s2 = [];
    for (const i2 of this.properties)
      s2.push(...i2.declare(e3, t2));
    return s2;
  }
  deoptimizePath(e3) {
    if (0 === e3.length)
      for (const t2 of this.properties)
        t2.deoptimizePath(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    for (const e4 of this.properties)
      if (e4.hasEffectsOnInteractionAtPath(H, t2, s2))
        return true;
    return false;
  }
  markDeclarationReached() {
    for (const e3 of this.properties)
      e3.markDeclarationReached();
  }
}
class bi extends Is {
  constructor(e3) {
    super("arguments", null, ne, e3);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > 1;
  }
}
class Ei extends Is {
  constructor(e3) {
    super("this", null, null, e3), this.deoptimizedPaths = [], this.entitiesToBeDeoptimized = /* @__PURE__ */ new Set(), this.thisDeoptimizationList = [], this.thisDeoptimizations = new ee();
  }
  addEntityToBeDeoptimized(e3) {
    for (const t2 of this.deoptimizedPaths)
      e3.deoptimizePath(t2);
    for (const { interaction: t2, path: s2 } of this.thisDeoptimizationList)
      e3.deoptimizeThisOnInteractionAtPath(t2, s2, Z);
    this.entitiesToBeDeoptimized.add(e3);
  }
  deoptimizePath(e3) {
    if (0 !== e3.length && !this.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e3, this)) {
      this.deoptimizedPaths.push(e3);
      for (const t2 of this.entitiesToBeDeoptimized)
        t2.deoptimizePath(e3);
    }
  }
  deoptimizeThisOnInteractionAtPath(e3, t2) {
    const s2 = { interaction: e3, path: t2 };
    if (!this.thisDeoptimizations.trackEntityAtPathAndGetIfTracked(t2, e3.type, e3.thisArg)) {
      for (const s3 of this.entitiesToBeDeoptimized)
        s3.deoptimizeThisOnInteractionAtPath(e3, t2, Z);
      this.thisDeoptimizationList.push(s2);
    }
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const i2 = s2.replacedVariableInits.get(this);
    return i2 ? i2.hasEffectsOnInteractionAtPath(e3, t2, s2) || !s2.ignore.this && super.hasEffectsOnInteractionAtPath(e3, t2, s2) : ne.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
}
class vi extends Ts {
  constructor(e3, t2) {
    super(e3, t2), this.variables.set("arguments", this.argumentsVariable = new bi(t2)), this.variables.set("this", this.thisVariable = new Ei(t2));
  }
  findLexicalBoundary() {
    return this;
  }
  includeCallArguments(e3, t2) {
    if (super.includeCallArguments(e3, t2), this.argumentsVariable.included)
      for (const s2 of t2)
        s2.included || s2.include(e3, false);
  }
}
class Si extends pi {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e3) {
    this.scope = new vi(e3, this.context), this.constructedEntity = new ls(/* @__PURE__ */ Object.create(null), us), this.scope.thisVariable.addEntityToBeDeoptimized(this.constructedEntity);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    super.deoptimizeThisOnInteractionAtPath(e3, t2, s2), 2 === e3.type && 0 === t2.length && this.scope.thisVariable.addEntityToBeDeoptimized(e3.thisArg);
  }
  hasEffects(e3) {
    var _a2;
    return this.deoptimized || this.applyDeoptimizations(), !!((_a2 = this.id) == null ? void 0 : _a2.hasEffects(e3));
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    if (super.hasEffectsOnInteractionAtPath(e3, t2, s2))
      return true;
    if (2 === t2.type) {
      const e4 = s2.replacedVariableInits.get(this.scope.thisVariable);
      s2.replacedVariableInits.set(this.scope.thisVariable, t2.withNew ? this.constructedEntity : ne);
      const { brokenFlow: i2, ignore: n2, replacedVariableInits: r2 } = s2;
      if (s2.ignore = { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: true, this: t2.withNew }, this.body.hasEffects(s2))
        return true;
      s2.brokenFlow = i2, e4 ? r2.set(this.scope.thisVariable, e4) : r2.delete(this.scope.thisVariable), s2.ignore = n2;
    }
    return false;
  }
  include(e3, t2) {
    var _a2;
    super.include(e3, t2), (_a2 = this.id) == null ? void 0 : _a2.include();
    const s2 = this.scope.argumentsVariable.included;
    for (const i2 of this.params)
      i2 instanceof Qs && !s2 || i2.include(e3, t2);
  }
  initialise() {
    var _a2;
    super.initialise(), (_a2 = this.id) == null ? void 0 : _a2.declare("function", this);
  }
  getObjectEntity() {
    return null !== this.objectEntity ? this.objectEntity : this.objectEntity = new ls([{ key: "prototype", kind: "init", property: new ls([], us) }], us);
  }
}
const ki = { "!=": (e3, t2) => e3 != t2, "!==": (e3, t2) => e3 !== t2, "%": (e3, t2) => e3 % t2, "&": (e3, t2) => e3 & t2, "*": (e3, t2) => e3 * t2, "**": (e3, t2) => e3 ** t2, "+": (e3, t2) => e3 + t2, "-": (e3, t2) => e3 - t2, "/": (e3, t2) => e3 / t2, "<": (e3, t2) => e3 < t2, "<<": (e3, t2) => e3 << t2, "<=": (e3, t2) => e3 <= t2, "==": (e3, t2) => e3 == t2, "===": (e3, t2) => e3 === t2, ">": (e3, t2) => e3 > t2, ">=": (e3, t2) => e3 >= t2, ">>": (e3, t2) => e3 >> t2, ">>>": (e3, t2) => e3 >>> t2, "^": (e3, t2) => e3 ^ t2, "|": (e3, t2) => e3 | t2 };
function Ai(e3, t2, s2) {
  if (s2.arguments.length > 0)
    if (s2.arguments[s2.arguments.length - 1].included)
      for (const i2 of s2.arguments)
        i2.render(e3, t2);
    else {
      let i2 = s2.arguments.length - 2;
      for (; i2 >= 0 && !s2.arguments[i2].included; )
        i2--;
      if (i2 >= 0) {
        for (let n2 = 0; n2 <= i2; n2++)
          s2.arguments[n2].render(e3, t2);
        e3.remove(si(e3.original, ",", s2.arguments[i2].end), s2.end - 1);
      } else
        e3.remove(si(e3.original, "(", s2.callee.end) + 1, s2.end - 1);
    }
}
class wi extends es {
  deoptimizeThisOnInteractionAtPath() {
  }
  getLiteralValueAtPath(e3) {
    return e3.length > 0 || null === this.value && 110 !== this.context.code.charCodeAt(this.start) || "bigint" == typeof this.value || 47 === this.context.code.charCodeAt(this.start) ? te : this.value;
  }
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 !== e3.length ? re : _t(this.members, e3[0]);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return e3.length > (null === this.value ? 0 : 1);
      case 1:
        return true;
      case 2:
        return !!(this.included && this.value instanceof RegExp && (this.value.global || this.value.sticky)) || (1 !== e3.length || Nt(this.members, e3[0], t2, s2));
    }
  }
  initialise() {
    this.members = function(e3) {
      if (e3 instanceof RegExp)
        return Ct;
      switch (typeof e3) {
        case "boolean":
          return It;
        case "number":
          return Pt;
        case "string":
          return $t;
      }
      return /* @__PURE__ */ Object.create(null);
    }(this.value);
  }
  parseNode(e3) {
    this.value = e3.value, this.regex = e3.regex, super.parseNode(e3);
  }
  render(e3) {
    "string" == typeof this.value && e3.indentExclusionRanges.push([this.start + 1, this.end - 1]);
  }
}
function Ii(e3) {
  return e3.computed ? function(e4) {
    if (e4 instanceof wi)
      return String(e4.value);
    return null;
  }(e3.property) : e3.property.name;
}
function Pi(e3) {
  const t2 = e3.propertyKey, s2 = e3.object;
  if ("string" == typeof t2) {
    if (s2 instanceof Qs)
      return [{ key: s2.name, pos: s2.start }, { key: t2, pos: e3.property.start }];
    if (s2 instanceof Ci) {
      const i2 = Pi(s2);
      return i2 && [...i2, { key: t2, pos: e3.property.start }];
    }
  }
  return null;
}
class Ci extends es {
  constructor() {
    super(...arguments), this.variable = null, this.assignmentDeoptimized = false, this.bound = false, this.expressionsToBeDeoptimized = [], this.isUndefined = false;
  }
  bind() {
    this.bound = true;
    const e3 = Pi(this), t2 = e3 && this.scope.findVariable(e3[0].key);
    if (t2 == null ? void 0 : t2.isNamespace) {
      const s2 = $i(t2, e3.slice(1), this.context);
      s2 ? "undefined" === s2 ? this.isUndefined = true : (this.variable = s2, this.scope.addNamespaceMemberAccess(function(e4) {
        let t3 = e4[0].key;
        for (let s3 = 1; s3 < e4.length; s3++)
          t3 += "." + e4[s3].key;
        return t3;
      }(e3), s2)) : super.bind();
    } else
      super.bind();
  }
  deoptimizeCache() {
    const e3 = this.expressionsToBeDeoptimized;
    this.expressionsToBeDeoptimized = [], this.propertyKey = U, this.object.deoptimizePath(K);
    for (const t2 of e3)
      t2.deoptimizeCache();
  }
  deoptimizePath(e3) {
    if (0 === e3.length && this.disallowNamespaceReassignment(), this.variable)
      this.variable.deoptimizePath(e3);
    else if (!this.isUndefined && e3.length < 7) {
      const t2 = this.getPropertyKey();
      this.object.deoptimizePath([t2 === U ? G : t2, ...e3]);
    }
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.variable ? this.variable.deoptimizeThisOnInteractionAtPath(e3, t2, s2) : this.isUndefined || (t2.length < 7 ? this.object.deoptimizeThisOnInteractionAtPath(e3, [this.getPropertyKey(), ...t2], s2) : e3.thisArg.deoptimizePath(K));
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.variable ? this.variable.getLiteralValueAtPath(e3, t2, s2) : this.isUndefined ? void 0 : (this.expressionsToBeDeoptimized.push(s2), e3.length < 7 ? this.object.getLiteralValueAtPath([this.getPropertyKey(), ...e3], t2, s2) : te);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.variable ? this.variable.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : this.isUndefined ? [gt, false] : (this.expressionsToBeDeoptimized.push(i2), e3.length < 7 ? this.object.getReturnExpressionWhenCalledAtPath([this.getPropertyKey(), ...e3], t2, s2, i2) : re);
  }
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), this.property.hasEffects(e3) || this.object.hasEffects(e3) || this.hasAccessEffect(e3);
  }
  hasEffectsAsAssignmentTarget(e3, t2) {
    return t2 && !this.deoptimized && this.applyDeoptimizations(), this.assignmentDeoptimized || this.applyAssignmentDeoptimization(), this.property.hasEffects(e3) || this.object.hasEffects(e3) || t2 && this.hasAccessEffect(e3) || this.hasEffectsOnInteractionAtPath(H, this.assignmentInteraction, e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.variable ? this.variable.hasEffectsOnInteractionAtPath(e3, t2, s2) : !!this.isUndefined || (!(e3.length < 7) || this.object.hasEffectsOnInteractionAtPath([this.getPropertyKey(), ...e3], t2, s2));
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.includeProperties(e3, t2);
  }
  includeAsAssignmentTarget(e3, t2, s2) {
    this.assignmentDeoptimized || this.applyAssignmentDeoptimization(), s2 ? this.include(e3, t2) : this.includeProperties(e3, t2);
  }
  includeCallArguments(e3, t2) {
    this.variable ? this.variable.includeCallArguments(e3, t2) : super.includeCallArguments(e3, t2);
  }
  initialise() {
    this.propertyKey = Ii(this), this.accessInteraction = { thisArg: this.object, type: 0 };
  }
  isSkippedAsOptional(e3) {
    var _a2, _b;
    return !this.variable && !this.isUndefined && (((_b = (_a2 = this.object).isSkippedAsOptional) == null ? void 0 : _b.call(_a2, e3)) || this.optional && null == this.object.getLiteralValueAtPath(H, Z, e3));
  }
  render(e3, t2, { renderedParentType: s2, isCalleeOfRenderedParent: i2, renderedSurroundingElement: n2 } = de) {
    if (this.variable || this.isUndefined) {
      const { snippets: { getPropertyAccess: n3 } } = t2;
      let r2 = this.variable ? this.variable.getName(n3) : "undefined";
      s2 && i2 && (r2 = "0, " + r2), e3.overwrite(this.start, this.end, r2, { contentOnly: true, storeName: true });
    } else
      s2 && i2 && e3.appendRight(this.start, "0, "), this.object.render(e3, t2, { renderedSurroundingElement: n2 }), this.property.render(e3, t2);
  }
  setAssignedValue(e3) {
    this.assignmentInteraction = { args: [e3], thisArg: this.object, type: 1 };
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    const { propertyReadSideEffects: e3 } = this.context.options.treeshake;
    if (this.bound && e3 && !this.variable && !this.isUndefined) {
      const e4 = this.getPropertyKey();
      this.object.deoptimizeThisOnInteractionAtPath(this.accessInteraction, [e4], Z), this.context.requestTreeshakingPass();
    }
  }
  applyAssignmentDeoptimization() {
    this.assignmentDeoptimized = true;
    const { propertyReadSideEffects: e3 } = this.context.options.treeshake;
    this.bound && e3 && !this.variable && !this.isUndefined && (this.object.deoptimizeThisOnInteractionAtPath(this.assignmentInteraction, [this.getPropertyKey()], Z), this.context.requestTreeshakingPass());
  }
  disallowNamespaceReassignment() {
    if (this.object instanceof Qs) {
      this.scope.findVariable(this.object.name).isNamespace && (this.variable && this.context.includeVariableInModule(this.variable), this.context.warn(qe(this.object.name, this.context.module.id), this.start));
    }
  }
  getPropertyKey() {
    if (null === this.propertyKey) {
      this.propertyKey = U;
      const e3 = this.property.getLiteralValueAtPath(H, Z, this);
      return this.propertyKey = e3 === q ? e3 : "symbol" == typeof e3 ? U : String(e3);
    }
    return this.propertyKey;
  }
  hasAccessEffect(e3) {
    const { propertyReadSideEffects: t2 } = this.context.options.treeshake;
    return !(this.variable || this.isUndefined) && t2 && ("always" === t2 || this.object.hasEffectsOnInteractionAtPath([this.getPropertyKey()], this.accessInteraction, e3));
  }
  includeProperties(e3, t2) {
    this.included || (this.included = true, this.variable && this.context.includeVariableInModule(this.variable)), this.object.include(e3, t2), this.property.include(e3, t2);
  }
}
function $i(e3, t2, s2) {
  if (0 === t2.length)
    return e3;
  if (!e3.isNamespace || e3 instanceof ue)
    return null;
  const i2 = t2[0].key, n2 = e3.context.traceExport(i2);
  if (!n2) {
    if (1 === t2.length) {
      const n3 = e3.context.fileName;
      return s2.warn(Qe(i2, s2.module.id, n3), t2[0].pos), "undefined";
    }
    return null;
  }
  return $i(n2, t2.slice(1), s2);
}
class Ni extends es {
  constructor() {
    super(...arguments), this.returnExpression = null, this.deoptimizableDependentExpressions = [], this.expressionsToBeDeoptimized = /* @__PURE__ */ new Set();
  }
  deoptimizeCache() {
    var _a2;
    if (((_a2 = this.returnExpression) == null ? void 0 : _a2[0]) !== ne) {
      this.returnExpression = re;
      for (const e3 of this.deoptimizableDependentExpressions)
        e3.deoptimizeCache();
      for (const e3 of this.expressionsToBeDeoptimized)
        e3.deoptimizePath(K);
    }
  }
  deoptimizePath(e3) {
    if (0 === e3.length || this.context.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e3, this))
      return;
    const [t2] = this.getReturnExpression();
    t2 !== ne && t2.deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    const [i2, n2] = this.getReturnExpression(s2);
    n2 || (i2 === ne ? e3.thisArg.deoptimizePath(K) : s2.withTrackedEntityAtPath(t2, i2, () => {
      this.expressionsToBeDeoptimized.add(e3.thisArg), i2.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
    }, null));
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const [i2] = this.getReturnExpression(t2);
    return i2 === ne ? te : t2.withTrackedEntityAtPath(e3, i2, () => (this.deoptimizableDependentExpressions.push(s2), i2.getLiteralValueAtPath(e3, t2, s2)), te);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const n2 = this.getReturnExpression(s2);
    return n2[0] === ne ? n2 : s2.withTrackedEntityAtPath(e3, n2, () => {
      this.deoptimizableDependentExpressions.push(i2);
      const [r2, o2] = n2[0].getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
      return [r2, o2 || n2[1]];
    }, re);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const { type: i2 } = t2;
    if (2 === i2) {
      const { args: i3, withNew: n3 } = t2;
      if ((n3 ? s2.instantiated : s2.called).trackEntityAtPathAndGetIfTracked(e3, i3, this))
        return false;
    } else if ((1 === i2 ? s2.assigned : s2.accessed).trackEntityAtPathAndGetIfTracked(e3, this))
      return false;
    const [n2, r2] = this.getReturnExpression();
    return (1 === i2 || !r2) && n2.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
}
class _i extends _s {
  addDeclaration(e3, t2, s2, i2) {
    const n2 = this.variables.get(e3.name);
    return n2 ? (this.parent.addDeclaration(e3, t2, gt, i2), n2.addDeclaration(e3, s2), n2) : this.parent.addDeclaration(e3, t2, s2, i2);
  }
}
class Ti extends Ns {
  constructor(e3, t2, s2) {
    super(e3), this.variables.set("this", this.thisVariable = new Is("this", null, t2, s2)), this.instanceScope = new Ns(this), this.instanceScope.variables.set("this", new Ei(s2));
  }
  findLexicalBoundary() {
    return this;
  }
}
class Ri extends es {
  constructor() {
    super(...arguments), this.accessedValue = null;
  }
  deoptimizeCache() {
  }
  deoptimizePath(e3) {
    this.getAccessedValue()[0].deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    return 0 === e3.type && "get" === this.kind && 0 === t2.length ? this.value.deoptimizeThisOnInteractionAtPath({ args: le, thisArg: e3.thisArg, type: 2, withNew: false }, H, s2) : 1 === e3.type && "set" === this.kind && 0 === t2.length ? this.value.deoptimizeThisOnInteractionAtPath({ args: e3.args, thisArg: e3.thisArg, type: 2, withNew: false }, H, s2) : void this.getAccessedValue()[0].deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getAccessedValue()[0].getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getAccessedValue()[0].getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffects(e3) {
    return this.key.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return "get" === this.kind && 0 === t2.type && 0 === e3.length ? this.value.hasEffectsOnInteractionAtPath(H, { args: le, thisArg: t2.thisArg, type: 2, withNew: false }, s2) : "set" === this.kind && 1 === t2.type ? this.value.hasEffectsOnInteractionAtPath(H, { args: t2.args, thisArg: t2.thisArg, type: 2, withNew: false }, s2) : this.getAccessedValue()[0].hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  applyDeoptimizations() {
  }
  getAccessedValue() {
    return null === this.accessedValue ? "get" === this.kind ? (this.accessedValue = re, this.accessedValue = this.value.getReturnExpressionWhenCalledAtPath(H, he, Z, this)) : this.accessedValue = [this.value, false] : this.accessedValue;
  }
}
class Mi extends Ri {
  applyDeoptimizations() {
  }
}
class Oi extends ie {
  constructor(e3, t2) {
    super(), this.object = e3, this.key = t2;
  }
  deoptimizePath(e3) {
    this.object.deoptimizePath([this.key, ...e3]);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.object.deoptimizeThisOnInteractionAtPath(e3, [this.key, ...t2], s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.object.getLiteralValueAtPath([this.key, ...e3], t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.object.getReturnExpressionWhenCalledAtPath([this.key, ...e3], t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.object.hasEffectsOnInteractionAtPath([this.key, ...e3], t2, s2);
  }
}
class Di extends es {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e3) {
    this.scope = new Ns(e3);
  }
  deoptimizeCache() {
    this.getObjectEntity().deoptimizeAllProperties();
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.getObjectEntity().deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffects(e3) {
    var _a2, _b;
    this.deoptimized || this.applyDeoptimizations();
    const t2 = ((_a2 = this.superClass) == null ? void 0 : _a2.hasEffects(e3)) || this.body.hasEffects(e3);
    return (_b = this.id) == null ? void 0 : _b.markDeclarationReached(), t2 || super.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    return 2 === t2.type && 0 === e3.length ? !t2.withNew || (null === this.classConstructor ? (_a2 = this.superClass) == null ? void 0 : _a2.hasEffectsOnInteractionAtPath(e3, t2, s2) : this.classConstructor.hasEffectsOnInteractionAtPath(e3, t2, s2)) || false : this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    var _a2;
    this.deoptimized || this.applyDeoptimizations(), this.included = true, (_a2 = this.superClass) == null ? void 0 : _a2.include(e3, t2), this.body.include(e3, t2), this.id && (this.id.markDeclarationReached(), this.id.include());
  }
  initialise() {
    var _a2;
    (_a2 = this.id) == null ? void 0 : _a2.declare("class", this);
    for (const e3 of this.body.body)
      if (e3 instanceof Mi && "constructor" === e3.kind)
        return void (this.classConstructor = e3);
    this.classConstructor = null;
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e3 of this.body.body)
      e3.static || e3 instanceof Mi && "constructor" === e3.kind || e3.deoptimizePath(K);
    this.context.requestTreeshakingPass();
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    const e3 = [], t2 = [];
    for (const s2 of this.body.body) {
      const i2 = s2.static ? e3 : t2, n2 = s2.kind;
      if (i2 === t2 && !n2)
        continue;
      const r2 = "set" === n2 || "get" === n2 ? n2 : "init";
      let o2;
      if (s2.computed) {
        const e4 = s2.key.getLiteralValueAtPath(H, Z, this);
        if ("symbol" == typeof e4) {
          i2.push({ key: U, kind: r2, property: s2 });
          continue;
        }
        o2 = String(e4);
      } else
        o2 = s2.key instanceof Qs ? s2.key.name : String(s2.key.value);
      i2.push({ key: o2, kind: r2, property: s2 });
    }
    return e3.unshift({ key: "prototype", kind: "init", property: new ls(t2, this.superClass ? new Oi(this.superClass, "prototype") : us) }), this.objectEntity = new ls(e3, this.superClass || us);
  }
}
class Li extends Di {
  initialise() {
    super.initialise(), null !== this.id && (this.id.variable.isId = true);
  }
  parseNode(e3) {
    null !== e3.id && (this.id = new Qs(e3.id, this, this.scope.parent)), super.parseNode(e3);
  }
  render(e3, t2) {
    var _a2;
    const { exportNamesByVariable: s2, format: i2, snippets: { _: n2, getPropertyAccess: r2 } } = t2;
    if (this.id) {
      const { variable: o2, name: a2 } = this.id;
      "system" === i2 && s2.has(o2) && e3.appendLeft(this.end, `${n2}${mi([o2], t2)};`);
      const l2 = o2.getName(r2);
      if (l2 !== a2)
        return (_a2 = this.superClass) == null ? void 0 : _a2.render(e3, t2), this.body.render(e3, { ...t2, useOriginalName: (e4) => e4 === o2 }), e3.prependRight(this.start, `let ${l2}${n2}=${n2}`), void e3.prependLeft(this.end, ";");
    }
    super.render(e3, t2);
  }
  applyDeoptimizations() {
    super.applyDeoptimizations();
    const { id: e3, scope: t2 } = this;
    if (e3) {
      const { name: s2, variable: i2 } = e3;
      for (const e4 of t2.accessedOutsideVariables.values())
        e4 !== i2 && e4.forbidName(s2);
    }
  }
}
class Vi extends Di {
  render(e3, t2, { renderedSurroundingElement: s2 } = de) {
    super.render(e3, t2), s2 === Vt && (e3.appendRight(this.start, "("), e3.prependLeft(this.end, ")"));
  }
}
class Bi extends ie {
  constructor(e3) {
    super(), this.expressions = e3, this.included = false;
  }
  deoptimizePath(e3) {
    for (const t2 of this.expressions)
      t2.deoptimizePath(e3);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return [new Bi(this.expressions.map((n2) => n2.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0])), false];
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    for (const i2 of this.expressions)
      if (i2.hasEffectsOnInteractionAtPath(e3, t2, s2))
        return true;
    return false;
  }
}
class Fi extends es {
  hasEffects() {
    return false;
  }
  initialise() {
    this.context.addExport(this);
  }
  render(e3, t2, s2) {
    e3.remove(s2.start, s2.end);
  }
  applyDeoptimizations() {
  }
}
Fi.prototype.needsBoundaries = true;
class zi extends Si {
  initialise() {
    super.initialise(), null !== this.id && (this.id.variable.isId = true);
  }
  parseNode(e3) {
    null !== e3.id && (this.id = new Qs(e3.id, this, this.scope.parent)), super.parseNode(e3);
  }
}
class ji extends es {
  include(e3, t2) {
    super.include(e3, t2), t2 && this.context.includeVariableInModule(this.variable);
  }
  initialise() {
    const e3 = this.declaration;
    this.declarationName = e3.id && e3.id.name || this.declaration.name, this.variable = this.scope.addExportDefaultDeclaration(this.declarationName || this.context.getModuleName(), this, this.context), this.context.addExport(this);
  }
  render(e3, t2, s2) {
    const { start: i2, end: n2 } = s2, r2 = function(e4, t3) {
      return ni(e4, si(e4, "default", t3) + 7);
    }(e3.original, this.start);
    if (this.declaration instanceof zi)
      this.renderNamedDeclaration(e3, r2, "function", "(", null === this.declaration.id, t2);
    else if (this.declaration instanceof Li)
      this.renderNamedDeclaration(e3, r2, "class", "{", null === this.declaration.id, t2);
    else {
      if (this.variable.getOriginalVariable() !== this.variable)
        return void Zs(this, e3, i2, n2);
      if (!this.variable.included)
        return e3.remove(this.start, r2), this.declaration.render(e3, t2, { renderedSurroundingElement: Vt }), void (";" !== e3.original[this.end - 1] && e3.appendLeft(this.end, ";"));
      this.renderVariableDeclaration(e3, r2, t2);
    }
    this.declaration.render(e3, t2);
  }
  applyDeoptimizations() {
  }
  renderNamedDeclaration(e3, t2, s2, i2, n2, r2) {
    const { exportNamesByVariable: o2, format: a2, snippets: { getPropertyAccess: l2 } } = r2, h2 = this.variable.getName(l2);
    e3.remove(this.start, t2), n2 && e3.appendLeft(function(e4, t3, s3, i3) {
      const n3 = si(e4, t3, i3) + t3.length;
      e4 = e4.slice(n3, si(e4, s3, n3));
      const r3 = si(e4, "*");
      return -1 === r3 ? n3 : n3 + r3 + 1;
    }(e3.original, s2, i2, t2), ` ${h2}`), "system" === a2 && this.declaration instanceof Li && o2.has(this.variable) && e3.appendLeft(this.end, ` ${mi([this.variable], r2)};`);
  }
  renderVariableDeclaration(e3, t2, { format: s2, exportNamesByVariable: i2, snippets: { cnst: n2, getPropertyAccess: r2 } }) {
    const o2 = 59 === e3.original.charCodeAt(this.end - 1), a2 = "system" === s2 && i2.get(this.variable);
    a2 ? (e3.overwrite(this.start, t2, `${n2} ${this.variable.getName(r2)} = exports('${a2[0]}', `), e3.appendRight(o2 ? this.end - 1 : this.end, ")" + (o2 ? "" : ";"))) : (e3.overwrite(this.start, t2, `${n2} ${this.variable.getName(r2)} = `), o2 || e3.appendLeft(this.end, ";"));
  }
}
ji.prototype.needsBoundaries = true;
class Ui extends es {
  bind() {
    var _a2;
    (_a2 = this.declaration) == null ? void 0 : _a2.bind();
  }
  hasEffects(e3) {
    var _a2;
    return !!((_a2 = this.declaration) == null ? void 0 : _a2.hasEffects(e3));
  }
  initialise() {
    this.context.addExport(this);
  }
  render(e3, t2, s2) {
    const { start: i2, end: n2 } = s2;
    null === this.declaration ? e3.remove(i2, n2) : (e3.remove(this.start, this.declaration.start), this.declaration.render(e3, t2, { end: n2, start: i2 }));
  }
  applyDeoptimizations() {
  }
}
Ui.prototype.needsBoundaries = true;
class Gi extends hi {
  constructor() {
    super(...arguments), this.hoistedDeclarations = [];
  }
  addDeclaration(e3, t2, s2, i2) {
    return this.hoistedDeclarations.push(e3), super.addDeclaration(e3, t2, s2, i2);
  }
}
const Wi = Symbol("unset");
class qi extends es {
  constructor() {
    super(...arguments), this.testValue = Wi;
  }
  deoptimizeCache() {
    this.testValue = te;
  }
  hasEffects(e3) {
    var _a2;
    if (this.test.hasEffects(e3))
      return true;
    const t2 = this.getTestValue();
    if ("symbol" == typeof t2) {
      const { brokenFlow: t3 } = e3;
      if (this.consequent.hasEffects(e3))
        return true;
      const s2 = e3.brokenFlow;
      return e3.brokenFlow = t3, null === this.alternate ? false : !!this.alternate.hasEffects(e3) || (e3.brokenFlow = e3.brokenFlow < s2 ? e3.brokenFlow : s2, false);
    }
    return t2 ? this.consequent.hasEffects(e3) : !!((_a2 = this.alternate) == null ? void 0 : _a2.hasEffects(e3));
  }
  include(e3, t2) {
    if (this.included = true, t2)
      this.includeRecursively(t2, e3);
    else {
      const t3 = this.getTestValue();
      "symbol" == typeof t3 ? this.includeUnknownTest(e3) : this.includeKnownTest(e3, t3);
    }
  }
  parseNode(e3) {
    this.consequentScope = new Gi(this.scope), this.consequent = new (this.context.getNodeConstructor(e3.consequent.type))(e3.consequent, this, this.consequentScope), e3.alternate && (this.alternateScope = new Gi(this.scope), this.alternate = new (this.context.getNodeConstructor(e3.alternate.type))(e3.alternate, this, this.alternateScope)), super.parseNode(e3);
  }
  render(e3, t2) {
    const { snippets: { getPropertyAccess: s2 } } = t2, i2 = this.getTestValue(), n2 = [], r2 = this.test.included, o2 = !this.context.options.treeshake;
    r2 ? this.test.render(e3, t2) : e3.remove(this.start, this.consequent.start), this.consequent.included && (o2 || "symbol" == typeof i2 || i2) ? this.consequent.render(e3, t2) : (e3.overwrite(this.consequent.start, this.consequent.end, r2 ? ";" : ""), n2.push(...this.consequentScope.hoistedDeclarations)), this.alternate && (!this.alternate.included || !o2 && "symbol" != typeof i2 && i2 ? (r2 && this.shouldKeepAlternateBranch() ? e3.overwrite(this.alternate.start, this.end, ";") : e3.remove(this.consequent.end, this.end), n2.push(...this.alternateScope.hoistedDeclarations)) : (r2 ? 101 === e3.original.charCodeAt(this.alternate.start - 1) && e3.prependLeft(this.alternate.start, " ") : e3.remove(this.consequent.end, this.alternate.start), this.alternate.render(e3, t2))), this.renderHoistedDeclarations(n2, e3, s2);
  }
  applyDeoptimizations() {
  }
  getTestValue() {
    return this.testValue === Wi ? this.testValue = this.test.getLiteralValueAtPath(H, Z, this) : this.testValue;
  }
  includeKnownTest(e3, t2) {
    var _a2;
    this.test.shouldBeIncluded(e3) && this.test.include(e3, false), t2 && this.consequent.shouldBeIncluded(e3) && this.consequent.include(e3, false, { asSingleStatement: true }), !t2 && ((_a2 = this.alternate) == null ? void 0 : _a2.shouldBeIncluded(e3)) && this.alternate.include(e3, false, { asSingleStatement: true });
  }
  includeRecursively(e3, t2) {
    var _a2;
    this.test.include(t2, e3), this.consequent.include(t2, e3), (_a2 = this.alternate) == null ? void 0 : _a2.include(t2, e3);
  }
  includeUnknownTest(e3) {
    var _a2;
    this.test.include(e3, false);
    const { brokenFlow: t2 } = e3;
    let s2 = 0;
    this.consequent.shouldBeIncluded(e3) && (this.consequent.include(e3, false, { asSingleStatement: true }), s2 = e3.brokenFlow, e3.brokenFlow = t2), ((_a2 = this.alternate) == null ? void 0 : _a2.shouldBeIncluded(e3)) && (this.alternate.include(e3, false, { asSingleStatement: true }), e3.brokenFlow = e3.brokenFlow < s2 ? e3.brokenFlow : s2);
  }
  renderHoistedDeclarations(e3, t2, s2) {
    const i2 = [...new Set(e3.map((e4) => {
      const t3 = e4.variable;
      return t3.included ? t3.getName(s2) : "";
    }))].filter(Boolean).join(", ");
    if (i2) {
      const e4 = this.parent.type, s3 = e4 !== Ft && e4 !== Dt;
      t2.prependRight(this.start, `${s3 ? "{ " : ""}var ${i2}; `), s3 && t2.appendLeft(this.end, " }");
    }
  }
  shouldKeepAlternateBranch() {
    let e3 = this.parent;
    do {
      if (e3 instanceof qi && e3.alternate)
        return true;
      if (e3 instanceof ui)
        return false;
      e3 = e3.parent;
    } while (e3);
    return false;
  }
}
class Hi extends es {
  bind() {
  }
  hasEffects() {
    return false;
  }
  initialise() {
    this.context.addImport(this);
  }
  render(e3, t2, s2) {
    e3.remove(s2.start, s2.end);
  }
  applyDeoptimizations() {
  }
}
Hi.prototype.needsBoundaries = true;
class Ki extends es {
  applyDeoptimizations() {
  }
}
const Yi = "_interopDefault", Xi = "_interopDefaultCompat", Qi = "_interopNamespace", Ji = "_interopNamespaceCompat", Zi = "_interopNamespaceDefault", en = "_interopNamespaceDefaultOnly", tn = "_mergeNamespaces", sn = { auto: Yi, compat: Xi, default: null, defaultOnly: null, esModule: null }, nn = (e3, t2) => "esModule" === e3 || t2 && ("auto" === e3 || "compat" === e3), rn = { auto: Qi, compat: Ji, default: Zi, defaultOnly: en, esModule: null }, on = (e3, t2) => "esModule" !== e3 && nn(e3, t2), an = (e3, t2, s2, i2, n2, r2, o2) => {
  const a2 = new Set(e3);
  for (const e4 of vn)
    t2.has(e4) && a2.add(e4);
  return vn.map((e4) => a2.has(e4) ? ln[e4](s2, i2, n2, r2, o2, a2) : "").join("");
}, ln = { [Xi](e3, t2, s2) {
  const { _: i2, getDirectReturnFunction: n2, n: r2 } = t2, [o2, a2] = n2(["e"], { functionReturn: true, lineBreakIndent: null, name: Xi });
  return `${o2}${un(t2)}${i2}?${i2}${s2 ? hn(t2) : cn(t2)}${a2}${r2}${r2}`;
}, [Yi](e3, t2, s2) {
  const { _: i2, getDirectReturnFunction: n2, n: r2 } = t2, [o2, a2] = n2(["e"], { functionReturn: true, lineBreakIndent: null, name: Yi });
  return `${o2}e${i2}&&${i2}e.__esModule${i2}?${i2}${s2 ? hn(t2) : cn(t2)}${a2}${r2}${r2}`;
}, [Ji](e3, t2, s2, i2, n2, r2) {
  const { _: o2, getDirectReturnFunction: a2, n: l2 } = t2;
  if (r2.has(Zi)) {
    const [e4, s3] = a2(["e"], { functionReturn: true, lineBreakIndent: null, name: Ji });
    return `${e4}${un(t2)}${o2}?${o2}e${o2}:${o2}${Zi}(e)${s3}${l2}${l2}`;
  }
  return `function ${Ji}(e)${o2}{${l2}${e3}if${o2}(${un(t2)})${o2}return e;${l2}` + dn(e3, e3, t2, s2, i2, n2) + `}${l2}${l2}`;
}, [en](e3, t2, s2, i2, n2) {
  const { getDirectReturnFunction: r2, getObject: o2, n: a2 } = t2, [l2, h2] = r2(["e"], { functionReturn: true, lineBreakIndent: null, name: en });
  return `${l2}${bn(i2, En(n2, o2([["__proto__", "null"], ["default", "e"]], { lineBreakIndent: null }), t2))}${h2}${a2}${a2}`;
}, [Zi](e3, t2, s2, i2, n2) {
  const { _: r2, n: o2 } = t2;
  return `function ${Zi}(e)${r2}{${o2}` + dn(e3, e3, t2, s2, i2, n2) + `}${o2}${o2}`;
}, [Qi](e3, t2, s2, i2, n2, r2) {
  const { _: o2, getDirectReturnFunction: a2, n: l2 } = t2;
  if (r2.has(Zi)) {
    const [e4, t3] = a2(["e"], { functionReturn: true, lineBreakIndent: null, name: Qi });
    return `${e4}e${o2}&&${o2}e.__esModule${o2}?${o2}e${o2}:${o2}${Zi}(e)${t3}${l2}${l2}`;
  }
  return `function ${Qi}(e)${o2}{${l2}${e3}if${o2}(e${o2}&&${o2}e.__esModule)${o2}return e;${l2}` + dn(e3, e3, t2, s2, i2, n2) + `}${l2}${l2}`;
}, [tn](e3, t2, s2, i2, n2) {
  const { _: r2, cnst: o2, n: a2 } = t2, l2 = "var" === o2 && s2;
  return `function ${tn}(n, m)${r2}{${a2}${e3}${fn(`{${a2}${e3}${e3}${e3}if${r2}(k${r2}!==${r2}'default'${r2}&&${r2}!(k in n))${r2}{${a2}` + (s2 ? l2 ? gn : yn : xn)(e3, e3 + e3 + e3 + e3, t2) + `${e3}${e3}${e3}}${a2}${e3}${e3}}`, l2, e3, t2)}${a2}${e3}return ${bn(i2, En(n2, "n", t2))};${a2}}${a2}${a2}`;
} }, hn = ({ _: e3, getObject: t2 }) => `e${e3}:${e3}${t2([["default", "e"]], { lineBreakIndent: null })}`, cn = ({ _: e3, getPropertyAccess: t2 }) => `e${t2("default")}${e3}:${e3}e`, un = ({ _: e3 }) => `e${e3}&&${e3}typeof e${e3}===${e3}'object'${e3}&&${e3}'default'${e3}in e`, dn = (e3, t2, s2, i2, n2, r2) => {
  const { _: o2, cnst: a2, getObject: l2, getPropertyAccess: h2, n: c2, s: u2 } = s2, d2 = `{${c2}` + (i2 ? mn : xn)(e3, t2 + e3 + e3, s2) + `${t2}${e3}}`;
  return `${t2}${a2} n${o2}=${o2}Object.create(null${r2 ? `,${o2}{${o2}[Symbol.toStringTag]:${o2}${Sn(l2)}${o2}}` : ""});${c2}${t2}if${o2}(e)${o2}{${c2}${t2}${e3}${pn(d2, !i2, s2)}${c2}${t2}}${c2}${t2}n${h2("default")}${o2}=${o2}e;${c2}${t2}return ${bn(n2, "n")}${u2}${c2}`;
}, pn = (e3, t2, { _: s2, cnst: i2, getFunctionIntro: n2, s: r2 }) => "var" !== i2 || t2 ? `for${s2}(${i2} k in e)${s2}${e3}` : `Object.keys(e).forEach(${n2(["k"], { isAsync: false, name: null })}${e3})${r2}`, fn = (e3, t2, s2, { _: i2, cnst: n2, getDirectReturnFunction: r2, getFunctionIntro: o2, n: a2 }) => {
  if (t2) {
    const [t3, n3] = r2(["e"], { functionReturn: false, lineBreakIndent: { base: s2, t: s2 }, name: null });
    return `m.forEach(${t3}e${i2}&&${i2}typeof e${i2}!==${i2}'string'${i2}&&${i2}!Array.isArray(e)${i2}&&${i2}Object.keys(e).forEach(${o2(["k"], { isAsync: false, name: null })}${e3})${n3});`;
  }
  return `for${i2}(var i${i2}=${i2}0;${i2}i${i2}<${i2}m.length;${i2}i++)${i2}{${a2}${s2}${s2}${n2} e${i2}=${i2}m[i];${a2}${s2}${s2}if${i2}(typeof e${i2}!==${i2}'string'${i2}&&${i2}!Array.isArray(e))${i2}{${i2}for${i2}(${n2} k in e)${i2}${e3}${i2}}${a2}${s2}}`;
}, mn = (e3, t2, s2) => {
  const { _: i2, n: n2 } = s2;
  return `${t2}if${i2}(k${i2}!==${i2}'default')${i2}{${n2}` + gn(e3, t2 + e3, s2) + `${t2}}${n2}`;
}, gn = (e3, t2, { _: s2, cnst: i2, getDirectReturnFunction: n2, n: r2 }) => {
  const [o2, a2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
  return `${t2}${i2} d${s2}=${s2}Object.getOwnPropertyDescriptor(e,${s2}k);${r2}${t2}Object.defineProperty(n,${s2}k,${s2}d.get${s2}?${s2}d${s2}:${s2}{${r2}${t2}${e3}enumerable:${s2}true,${r2}${t2}${e3}get:${s2}${o2}e[k]${a2}${r2}${t2}});${r2}`;
}, yn = (e3, t2, { _: s2, cnst: i2, getDirectReturnFunction: n2, n: r2 }) => {
  const [o2, a2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
  return `${t2}${i2} d${s2}=${s2}Object.getOwnPropertyDescriptor(e,${s2}k);${r2}${t2}if${s2}(d)${s2}{${r2}${t2}${e3}Object.defineProperty(n,${s2}k,${s2}d.get${s2}?${s2}d${s2}:${s2}{${r2}${t2}${e3}${e3}enumerable:${s2}true,${r2}${t2}${e3}${e3}get:${s2}${o2}e[k]${a2}${r2}${t2}${e3}});${r2}${t2}}${r2}`;
}, xn = (e3, t2, { _: s2, n: i2 }) => `${t2}n[k]${s2}=${s2}e[k];${i2}`, bn = (e3, t2) => e3 ? `Object.freeze(${t2})` : t2, En = (e3, t2, { _: s2, getObject: i2 }) => e3 ? `Object.defineProperty(${t2},${s2}Symbol.toStringTag,${s2}${Sn(i2)})` : t2, vn = Object.keys(ln);
function Sn(e3) {
  return e3([["value", "'Module'"]], { lineBreakIndent: null });
}
function kn(e3, t2, s2) {
  return "external" === t2 ? rn[s2(e3 instanceof ct ? e3.id : null)] : "default" === t2 ? en : null;
}
const An = { amd: ["require"], cjs: ["require"], system: ["module"] };
class wn extends es {
  applyDeoptimizations() {
  }
}
const In = "ROLLUP_FILE_URL_", Pn = "import";
const Cn = { amd: ["document", "module", "URL"], cjs: ["document", "require", "URL"], es: [], iife: ["document", "URL"], system: ["module"], umd: ["document", "require", "URL"] }, $n = { amd: ["document", "require", "URL"], cjs: ["document", "require", "URL"], es: [], iife: ["document", "URL"], system: ["module", "URL"], umd: ["document", "require", "URL"] }, Nn = (e3, t2 = "URL") => `new ${t2}(${e3}).href`, _n = (e3, t2 = false) => Nn(`'${R(e3)}', ${t2 ? "typeof document === 'undefined' ? location.href : " : ""}document.currentScript && document.currentScript.src || document.baseURI`), Tn = (e3) => (t2, { chunkId: s2 }) => {
  const i2 = e3(s2);
  return null === t2 ? `({ url: ${i2} })` : "url" === t2 ? i2 : "undefined";
}, Rn = (e3) => `require('u' + 'rl').pathToFileURL(${e3}).href`, Mn = (e3) => Rn(`__dirname + '/${e3}'`), On = (e3, t2 = false) => `${t2 ? "typeof document === 'undefined' ? location.href : " : ""}(document.currentScript && document.currentScript.src || new URL('${R(e3)}', document.baseURI).href)`, Dn = { amd: (e3) => ("." !== e3[0] && (e3 = "./" + e3), Nn(`require.toUrl('${e3}'), document.baseURI`)), cjs: (e3) => `(typeof document === 'undefined' ? ${Mn(e3)} : ${_n(e3)})`, es: (e3) => Nn(`'${e3}', import.meta.url`), iife: (e3) => _n(e3), system: (e3) => Nn(`'${e3}', module.meta.url`), umd: (e3) => `(typeof document === 'undefined' && typeof location === 'undefined' ? ${Mn(e3)} : ${_n(e3, true)})` }, Ln = { amd: Tn(() => Nn("module.uri, document.baseURI")), cjs: Tn((e3) => `(typeof document === 'undefined' ? ${Rn("__filename")} : ${On(e3)})`), iife: Tn((e3) => On(e3)), system: (e3, { snippets: { getPropertyAccess: t2 } }) => null === e3 ? "module.meta" : `module.meta${t2(e3)}`, umd: Tn((e3) => `(typeof document === 'undefined' && typeof location === 'undefined' ? ${Rn("__filename")} : ${On(e3, true)})`) };
function Vn(e3, t2) {
  const s2 = e3.filter((e4) => !!e4.mappings);
  e:
    for (; s2.length > 0; ) {
      const e4 = s2.pop().mappings[t2.line - 1];
      if (e4) {
        const s3 = e4.filter((e5) => e5.length > 1), i2 = s3[s3.length - 1];
        for (const e5 of s3)
          if (e5[0] >= t2.column || e5 === i2) {
            t2 = { column: e5[3], line: e5[2] + 1 };
            continue e;
          }
      }
      throw new Error("Can't resolve original location of error.");
    }
  return t2;
}
class Bn extends es {
  constructor() {
    super(...arguments), this.hasCachedEffect = null, this.hasLoggedEffect = false;
  }
  hasCachedEffects() {
    return null === this.hasCachedEffect ? this.hasCachedEffect = this.hasEffects(ft()) : this.hasCachedEffect;
  }
  hasEffects(e3) {
    for (const t2 of this.body)
      if (t2.hasEffects(e3)) {
        if (this.context.options.experimentalLogSideEffects && !this.hasLoggedEffect) {
          this.hasLoggedEffect = true;
          const { code: e4, module: s2 } = this.context, { line: i2, column: n2 } = me(e4, t2.start, { offsetLine: 1 });
          console.log(`First side effect in ${O(s2.id)} is at (${i2}:${n2})
${xe(e4, i2, n2)}`);
          try {
            const { column: e5, line: t3 } = Vn(s2.sourcemapChain, { column: n2, line: i2 });
            t3 !== i2 && console.log(`Original location is at (${t3}:${e5})
${xe(s2.originalCode, t3, e5)}
`);
          } catch {
          }
          console.log();
        }
        return this.hasCachedEffect = true;
      }
    return false;
  }
  include(e3, t2) {
    this.included = true;
    for (const s2 of this.body)
      (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
  }
  render(e3, t2) {
    let s2 = this.start;
    e3.original.startsWith("#!") && (s2 = Math.min(e3.original.indexOf("\n") + 1, this.end), e3.remove(0, s2)), this.body.length > 0 ? oi(this.body, e3, s2, this.end, t2) : super.render(e3, t2);
  }
  applyDeoptimizations() {
  }
}
class Fn extends es {
  hasEffects(e3) {
    var _a2;
    if ((_a2 = this.test) == null ? void 0 : _a2.hasEffects(e3))
      return true;
    for (const t2 of this.consequent) {
      if (e3.brokenFlow)
        break;
      if (t2.hasEffects(e3))
        return true;
    }
    return false;
  }
  include(e3, t2) {
    var _a2;
    this.included = true, (_a2 = this.test) == null ? void 0 : _a2.include(e3, t2);
    for (const s2 of this.consequent)
      (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
  }
  render(e3, t2, s2) {
    if (this.consequent.length > 0) {
      this.test && this.test.render(e3, t2);
      const i2 = this.test ? this.test.end : si(e3.original, "default", this.start) + 7, n2 = si(e3.original, ":", i2) + 1;
      oi(this.consequent, e3, n2, s2.end, t2);
    } else
      super.render(e3, t2);
  }
}
Fn.prototype.needsBoundaries = true;
class zn extends es {
  deoptimizeThisOnInteractionAtPath() {
  }
  getLiteralValueAtPath(e3) {
    return e3.length > 0 || 1 !== this.quasis.length ? te : this.quasis[0].value.cooked;
  }
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 !== e3.length ? re : _t($t, e3[0]);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || Nt($t, e3[0], t2, s2);
  }
  render(e3, t2) {
    e3.indentExclusionRanges.push([this.start, this.end]), super.render(e3, t2);
  }
}
class jn extends ce {
  constructor() {
    super("undefined");
  }
  getLiteralValueAtPath() {
  }
}
class Un extends Is {
  constructor(e3, t2, s2) {
    super(e3, t2, t2.declaration, s2), this.hasId = false, this.originalId = null, this.originalVariable = null;
    const i2 = t2.declaration;
    (i2 instanceof zi || i2 instanceof Li) && i2.id ? (this.hasId = true, this.originalId = i2.id) : i2 instanceof Qs && (this.originalId = i2);
  }
  addReference(e3) {
    this.hasId || (this.name = e3.name);
  }
  forbidName(e3) {
    const t2 = this.getOriginalVariable();
    t2 === this ? super.forbidName(e3) : t2.forbidName(e3);
  }
  getAssignedVariableName() {
    return this.originalId && this.originalId.name || null;
  }
  getBaseVariableName() {
    const e3 = this.getOriginalVariable();
    return e3 === this ? super.getBaseVariableName() : e3.getBaseVariableName();
  }
  getDirectOriginalVariable() {
    return !this.originalId || !this.hasId && (this.originalId.isPossibleTDZ() || this.originalId.variable.isReassigned || this.originalId.variable instanceof jn || "syntheticNamespace" in this.originalId.variable) ? null : this.originalId.variable;
  }
  getName(e3) {
    const t2 = this.getOriginalVariable();
    return t2 === this ? super.getName(e3) : t2.getName(e3);
  }
  getOriginalVariable() {
    if (this.originalVariable)
      return this.originalVariable;
    let e3, t2 = this;
    const s2 = /* @__PURE__ */ new Set();
    do {
      s2.add(t2), e3 = t2, t2 = e3.getDirectOriginalVariable();
    } while (t2 instanceof Un && !s2.has(t2));
    return this.originalVariable = t2 || e3;
  }
}
class Gn extends Ns {
  constructor(e3, t2) {
    super(e3), this.context = t2, this.variables.set("this", new Is("this", null, gt, t2));
  }
  addExportDefaultDeclaration(e3, t2, s2) {
    const i2 = new Un(e3, t2, s2);
    return this.variables.set("default", i2), i2;
  }
  addNamespaceMemberAccess() {
  }
  deconflict(e3, t2, s2) {
    for (const i2 of this.children)
      i2.deconflict(e3, t2, s2);
  }
  findLexicalBoundary() {
    return this;
  }
  findVariable(e3) {
    const t2 = this.variables.get(e3) || this.accessedOutsideVariables.get(e3);
    if (t2)
      return t2;
    const s2 = this.context.traceVariable(e3) || this.parent.findVariable(e3);
    return s2 instanceof Ys && this.accessedOutsideVariables.set(e3, s2), s2;
  }
}
const Wn = { "!": (e3) => !e3, "+": (e3) => +e3, "-": (e3) => -e3, delete: () => te, typeof: (e3) => typeof e3, void: () => {
}, "~": (e3) => ~e3 };
function qn(e3, t2) {
  return null !== e3.renderBaseName && t2.has(e3) && e3.isReassigned;
}
class Hn extends es {
  deoptimizePath() {
    for (const e3 of this.declarations)
      e3.deoptimizePath(H);
  }
  hasEffectsOnInteractionAtPath() {
    return false;
  }
  include(e3, t2, { asSingleStatement: s2 } = de) {
    this.included = true;
    for (const i2 of this.declarations) {
      (t2 || i2.shouldBeIncluded(e3)) && i2.include(e3, t2);
      const { id: n2, init: r2 } = i2;
      s2 && n2.include(e3, t2), r2 && n2.included && !r2.included && (n2 instanceof xi || n2 instanceof ws) && r2.include(e3, t2);
    }
  }
  initialise() {
    for (const e3 of this.declarations)
      e3.declareDeclarator(this.kind);
  }
  render(e3, t2, s2 = de) {
    if (function(e4, t3) {
      for (const s3 of e4) {
        if (!s3.id.included)
          return false;
        if (s3.id.type === Bt) {
          if (t3.has(s3.id.variable))
            return false;
        } else {
          const e5 = [];
          if (s3.id.addExportedVariables(e5, t3), e5.length > 0)
            return false;
        }
      }
      return true;
    }(this.declarations, t2.exportNamesByVariable)) {
      for (const s3 of this.declarations)
        s3.render(e3, t2);
      s2.isNoStatement || 59 === e3.original.charCodeAt(this.end - 1) || e3.appendLeft(this.end, ";");
    } else
      this.renderReplacedDeclarations(e3, t2);
  }
  applyDeoptimizations() {
  }
  renderDeclarationEnd(e3, t2, s2, i2, n2, r2, o2) {
    59 === e3.original.charCodeAt(this.end - 1) && e3.remove(this.end - 1, this.end), t2 += ";", null === s2 ? e3.appendLeft(n2, t2) : (10 !== e3.original.charCodeAt(i2 - 1) || 10 !== e3.original.charCodeAt(this.end) && 13 !== e3.original.charCodeAt(this.end) || (i2--, 13 === e3.original.charCodeAt(i2) && i2--), i2 === s2 + 1 ? e3.overwrite(s2, n2, t2) : (e3.overwrite(s2, s2 + 1, t2), e3.remove(i2, n2))), r2.length > 0 && e3.appendLeft(n2, ` ${mi(r2, o2)};`);
  }
  renderReplacedDeclarations(e3, t2) {
    const s2 = ai(this.declarations, e3, this.start + this.kind.length, this.end - (59 === e3.original.charCodeAt(this.end - 1) ? 1 : 0));
    let i2, n2;
    n2 = ni(e3.original, this.start + this.kind.length);
    let r2 = n2 - 1;
    e3.remove(this.start, r2);
    let o2, l2 = false, h2 = false, c2 = "";
    const u2 = [], d2 = function(e4, t3, s3) {
      var _a2;
      let i3 = null;
      if ("system" === t3.format) {
        for (const { node: n3 } of e4)
          n3.id instanceof Qs && n3.init && 0 === s3.length && 1 === ((_a2 = t3.exportNamesByVariable.get(n3.id.variable)) == null ? void 0 : _a2.length) ? (i3 = n3.id.variable, s3.push(i3)) : n3.id.addExportedVariables(s3, t3.exportNamesByVariable);
        s3.length > 1 ? i3 = null : i3 && (s3.length = 0);
      }
      return i3;
    }(s2, t2, u2);
    for (const { node: u3, start: p2, separator: f2, contentEnd: m2, end: g2 } of s2)
      if (u3.included) {
        if (u3.render(e3, t2), o2 = "", !u3.id.included || u3.id instanceof Qs && qn(u3.id.variable, t2.exportNamesByVariable))
          h2 && (c2 += ";"), l2 = false;
        else {
          if (d2 && d2 === u3.id.variable) {
            const s3 = si(e3.original, "=", u3.id.end);
            gi(d2, ni(e3.original, s3 + 1), null === f2 ? m2 : f2, e3, t2);
          }
          l2 ? c2 += "," : (h2 && (c2 += ";"), o2 += `${this.kind} `, l2 = true);
        }
        n2 === r2 + 1 ? e3.overwrite(r2, n2, c2 + o2) : (e3.overwrite(r2, r2 + 1, c2), e3.appendLeft(n2, o2)), i2 = m2, n2 = g2, h2 = true, r2 = f2, c2 = "";
      } else
        e3.remove(p2, g2);
    this.renderDeclarationEnd(e3, c2, r2, i2, n2, u2, t2);
  }
}
const Kn = { ArrayExpression: class extends es {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.getObjectEntity().deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    let e3 = false;
    for (let t2 = 0; t2 < this.elements.length; t2++) {
      const s2 = this.elements[t2];
      s2 && (e3 || s2 instanceof ts) && (e3 = true, s2.deoptimizePath(K));
    }
    this.context.requestTreeshakingPass();
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    const e3 = [{ key: "length", kind: "init", property: Et }];
    let t2 = false;
    for (let s2 = 0; s2 < this.elements.length; s2++) {
      const i2 = this.elements[s2];
      t2 || i2 instanceof ts ? i2 && (t2 = true, e3.unshift({ key: W, kind: "init", property: i2 })) : i2 ? e3.push({ key: String(s2), kind: "init", property: i2 }) : e3.push({ key: String(s2), kind: "init", property: gt });
    }
    return this.objectEntity = new ls(e3, As);
  }
}, ArrayPattern: ws, ArrowFunctionExpression: fi, AssignmentExpression: class extends es {
  hasEffects(e3) {
    const { deoptimized: t2, left: s2, operator: i2, right: n2 } = this;
    return t2 || this.applyDeoptimizations(), n2.hasEffects(e3) || s2.hasEffectsAsAssignmentTarget(e3, "=" !== i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.right.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    const { deoptimized: s2, left: i2, right: n2, operator: r2 } = this;
    s2 || this.applyDeoptimizations(), this.included = true, (t2 || "=" !== r2 || i2.included || i2.hasEffectsAsAssignmentTarget(ft(), false)) && i2.includeAsAssignmentTarget(e3, t2, "=" !== r2), n2.include(e3, t2);
  }
  initialise() {
    this.left.setAssignedValue(this.right);
  }
  render(e3, t2, { preventASI: s2, renderedParentType: i2, renderedSurroundingElement: n2 } = de) {
    const { left: r2, right: o2, start: a2, end: l2, parent: h2 } = this;
    if (r2.included)
      r2.render(e3, t2), o2.render(e3, t2);
    else {
      const l3 = ni(e3.original, si(e3.original, "=", r2.end) + 1);
      e3.remove(a2, l3), s2 && li(e3, l3, o2.start), o2.render(e3, t2, { renderedParentType: i2 || h2.type, renderedSurroundingElement: n2 || h2.type });
    }
    if ("system" === t2.format)
      if (r2 instanceof Qs) {
        const s3 = r2.variable, i3 = t2.exportNamesByVariable.get(s3);
        if (i3)
          return void (1 === i3.length ? gi(s3, a2, l2, e3, t2) : yi(s3, a2, l2, h2.type !== Vt, e3, t2));
      } else {
        const s3 = [];
        if (r2.addExportedVariables(s3, t2.exportNamesByVariable), s3.length > 0)
          return void function(e4, t3, s4, i3, n3, r3) {
            const { _: o3, getDirectReturnIifeLeft: a3 } = r3.snippets;
            n3.prependRight(t3, a3(["v"], `${mi(e4, r3)},${o3}v`, { needsArrowReturnParens: true, needsWrappedFunction: i3 })), n3.appendLeft(s4, ")");
          }(s3, a2, l2, n2 === Vt, e3, t2);
      }
    r2.included && r2 instanceof xi && (n2 === Vt || n2 === Ot) && (e3.appendRight(a2, "("), e3.prependLeft(l2, ")"));
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(H), this.right.deoptimizePath(K), this.context.requestTreeshakingPass();
  }
}, AssignmentPattern: class extends es {
  addExportedVariables(e3, t2) {
    this.left.addExportedVariables(e3, t2);
  }
  declare(e3, t2) {
    return this.left.declare(e3, t2);
  }
  deoptimizePath(e3) {
    0 === e3.length && this.left.deoptimizePath(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return e3.length > 0 || this.left.hasEffectsOnInteractionAtPath(H, t2, s2);
  }
  markDeclarationReached() {
    this.left.markDeclarationReached();
  }
  render(e3, t2, { isShorthandProperty: s2 } = de) {
    this.left.render(e3, t2, { isShorthandProperty: s2 }), this.right.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(H), this.right.deoptimizePath(K), this.context.requestTreeshakingPass();
  }
}, AwaitExpression: class extends es {
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), true;
  }
  include(e3, t2) {
    if (this.deoptimized || this.applyDeoptimizations(), !this.included) {
      this.included = true;
      e:
        if (!this.context.usesTopLevelAwait) {
          let e4 = this.parent;
          do {
            if (e4 instanceof Si || e4 instanceof fi)
              break e;
          } while (e4 = e4.parent);
          this.context.usesTopLevelAwait = true;
        }
    }
    this.argument.include(e3, t2);
  }
}, BinaryExpression: class extends es {
  deoptimizeCache() {
  }
  getLiteralValueAtPath(e3, t2, s2) {
    if (e3.length > 0)
      return te;
    const i2 = this.left.getLiteralValueAtPath(H, t2, s2);
    if ("symbol" == typeof i2)
      return te;
    const n2 = this.right.getLiteralValueAtPath(H, t2, s2);
    if ("symbol" == typeof n2)
      return te;
    const r2 = ki[this.operator];
    return r2 ? r2(i2, n2) : te;
  }
  hasEffects(e3) {
    return "+" === this.operator && this.parent instanceof ci && "" === this.left.getLiteralValueAtPath(H, Z, this) || super.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > 1;
  }
  render(e3, t2, { renderedSurroundingElement: s2 } = de) {
    this.left.render(e3, t2, { renderedSurroundingElement: s2 }), this.right.render(e3, t2);
  }
}, BlockStatement: ui, BreakStatement: class extends es {
  hasEffects(e3) {
    if (this.label) {
      if (!e3.ignore.labels.has(this.label.name))
        return true;
      e3.includedLabels.add(this.label.name), e3.brokenFlow = 2;
    } else {
      if (!e3.ignore.breaks)
        return true;
      e3.brokenFlow = 1;
    }
    return false;
  }
  include(e3) {
    this.included = true, this.label && (this.label.include(), e3.includedLabels.add(this.label.name)), e3.brokenFlow = this.label ? 2 : 1;
  }
}, CallExpression: class extends Ni {
  bind() {
    if (super.bind(), this.callee instanceof Qs) {
      this.scope.findVariable(this.callee.name).isNamespace && this.context.warn(Ue(this.callee.name), this.start), "eval" === this.callee.name && this.context.warn({ code: "EVAL", id: e3 = this.context.module.id, message: `Use of eval in "${O(e3)}" is strongly discouraged as it poses security risks and may cause issues with minification.`, url: Ee("troubleshooting/#avoiding-eval") }, this.start);
    }
    var e3;
    this.interaction = { args: this.arguments, thisArg: this.callee instanceof Ci && !this.callee.variable ? this.callee.object : null, type: 2, withNew: false };
  }
  hasEffects(e3) {
    try {
      for (const t2 of this.arguments)
        if (t2.hasEffects(e3))
          return true;
      return (!this.context.options.treeshake.annotations || !this.annotations) && (this.callee.hasEffects(e3) || this.callee.hasEffectsOnInteractionAtPath(H, this.interaction, e3));
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? (super.include(e3, t2), t2 === Zt && this.callee instanceof Qs && this.callee.variable && this.callee.variable.markCalledFromTryStatement()) : (this.included = true, this.callee.include(e3, false)), this.callee.includeCallArguments(e3, this.arguments);
  }
  isSkippedAsOptional(e3) {
    var _a2, _b;
    return ((_b = (_a2 = this.callee).isSkippedAsOptional) == null ? void 0 : _b.call(_a2, e3)) || this.optional && null == this.callee.getLiteralValueAtPath(H, Z, e3);
  }
  render(e3, t2, { renderedSurroundingElement: s2 } = de) {
    this.callee.render(e3, t2, { isCalleeOfRenderedParent: true, renderedSurroundingElement: s2 }), Ai(e3, t2, this);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.interaction.thisArg && this.callee.deoptimizeThisOnInteractionAtPath(this.interaction, H, Z);
    for (const e3 of this.arguments)
      e3.deoptimizePath(K);
    this.context.requestTreeshakingPass();
  }
  getReturnExpression(e3 = Z) {
    return null === this.returnExpression ? (this.returnExpression = re, this.returnExpression = this.callee.getReturnExpressionWhenCalledAtPath(H, this.interaction, e3, this)) : this.returnExpression;
  }
}, CatchClause: class extends es {
  createScope(e3) {
    this.scope = new _i(e3, this.context);
  }
  parseNode(e3) {
    const { param: t2 } = e3;
    t2 && (this.param = new (this.context.getNodeConstructor(t2.type))(t2, this, this.scope), this.param.declare("parameter", ne)), super.parseNode(e3);
  }
}, ChainExpression: class extends es {
  deoptimizeCache() {
  }
  getLiteralValueAtPath(e3, t2, s2) {
    if (!this.expression.isSkippedAsOptional(s2))
      return this.expression.getLiteralValueAtPath(e3, t2, s2);
  }
  hasEffects(e3) {
    return !this.expression.isSkippedAsOptional(this) && this.expression.hasEffects(e3);
  }
}, ClassBody: class extends es {
  createScope(e3) {
    this.scope = new Ti(e3, this.parent, this.context);
  }
  include(e3, t2) {
    this.included = true, this.context.includeVariableInModule(this.scope.thisVariable);
    for (const s2 of this.body)
      s2.include(e3, t2);
  }
  parseNode(e3) {
    const t2 = this.body = [];
    for (const s2 of e3.body)
      t2.push(new (this.context.getNodeConstructor(s2.type))(s2, this, s2.static ? this.scope : this.scope.instanceScope));
    super.parseNode(e3);
  }
  applyDeoptimizations() {
  }
}, ClassDeclaration: Li, ClassExpression: Vi, ConditionalExpression: class extends es {
  constructor() {
    super(...arguments), this.expressionsToBeDeoptimized = [], this.isBranchResolutionAnalysed = false, this.usedBranch = null;
  }
  deoptimizeCache() {
    if (null !== this.usedBranch) {
      const e3 = this.usedBranch === this.consequent ? this.alternate : this.consequent;
      this.usedBranch = null, e3.deoptimizePath(K);
      for (const e4 of this.expressionsToBeDeoptimized)
        e4.deoptimizeCache();
    }
  }
  deoptimizePath(e3) {
    const t2 = this.getUsedBranch();
    t2 ? t2.deoptimizePath(e3) : (this.consequent.deoptimizePath(e3), this.alternate.deoptimizePath(e3));
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.consequent.deoptimizeThisOnInteractionAtPath(e3, t2, s2), this.alternate.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? (this.expressionsToBeDeoptimized.push(s2), i2.getLiteralValueAtPath(e3, t2, s2)) : te;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const n2 = this.getUsedBranch();
    return n2 ? (this.expressionsToBeDeoptimized.push(i2), n2.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)) : [new Bi([this.consequent.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0], this.alternate.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0]]), false];
  }
  hasEffects(e3) {
    if (this.test.hasEffects(e3))
      return true;
    const t2 = this.getUsedBranch();
    return t2 ? t2.hasEffects(e3) : this.consequent.hasEffects(e3) || this.alternate.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? i2.hasEffectsOnInteractionAtPath(e3, t2, s2) : this.consequent.hasEffectsOnInteractionAtPath(e3, t2, s2) || this.alternate.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = this.getUsedBranch();
    t2 || this.test.shouldBeIncluded(e3) || null === s2 ? (this.test.include(e3, t2), this.consequent.include(e3, t2), this.alternate.include(e3, t2)) : s2.include(e3, t2);
  }
  includeCallArguments(e3, t2) {
    const s2 = this.getUsedBranch();
    s2 ? s2.includeCallArguments(e3, t2) : (this.consequent.includeCallArguments(e3, t2), this.alternate.includeCallArguments(e3, t2));
  }
  render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2, renderedSurroundingElement: r2 } = de) {
    const o2 = this.getUsedBranch();
    if (this.test.included)
      this.test.render(e3, t2, { renderedSurroundingElement: r2 }), this.consequent.render(e3, t2), this.alternate.render(e3, t2);
    else {
      const a2 = si(e3.original, ":", this.consequent.end), l2 = ni(e3.original, (this.consequent.included ? si(e3.original, "?", this.test.end) : a2) + 1);
      i2 && li(e3, l2, o2.start), e3.remove(this.start, l2), this.consequent.included && e3.remove(a2, this.end), ei(this, e3), o2.render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: true, renderedParentType: n2 || this.parent.type, renderedSurroundingElement: r2 || this.parent.type });
    }
  }
  getUsedBranch() {
    if (this.isBranchResolutionAnalysed)
      return this.usedBranch;
    this.isBranchResolutionAnalysed = true;
    const e3 = this.test.getLiteralValueAtPath(H, Z, this);
    return "symbol" == typeof e3 ? null : this.usedBranch = e3 ? this.consequent : this.alternate;
  }
}, ContinueStatement: class extends es {
  hasEffects(e3) {
    if (this.label) {
      if (!e3.ignore.labels.has(this.label.name))
        return true;
      e3.includedLabels.add(this.label.name), e3.brokenFlow = 2;
    } else {
      if (!e3.ignore.continues)
        return true;
      e3.brokenFlow = 1;
    }
    return false;
  }
  include(e3) {
    this.included = true, this.label && (this.label.include(), e3.includedLabels.add(this.label.name)), e3.brokenFlow = this.label ? 2 : 1;
  }
}, DoWhileStatement: class extends es {
  hasEffects(e3) {
    if (this.test.hasEffects(e3))
      return true;
    const { brokenFlow: t2, ignore: s2 } = e3, { breaks: i2, continues: n2 } = s2;
    return s2.breaks = true, s2.continues = true, !!this.body.hasEffects(e3) || (s2.breaks = i2, s2.continues = n2, e3.brokenFlow = t2, false);
  }
  include(e3, t2) {
    this.included = true, this.test.include(e3, t2);
    const { brokenFlow: s2 } = e3;
    this.body.include(e3, t2, { asSingleStatement: true }), e3.brokenFlow = s2;
  }
}, EmptyStatement: class extends es {
  hasEffects() {
    return false;
  }
}, ExportAllDeclaration: Fi, ExportDefaultDeclaration: ji, ExportNamedDeclaration: Ui, ExportSpecifier: class extends es {
  applyDeoptimizations() {
  }
}, ExpressionStatement: ci, ForInStatement: class extends es {
  createScope(e3) {
    this.scope = new hi(e3);
  }
  hasEffects(e3) {
    const { body: t2, deoptimized: s2, left: i2, right: n2 } = this;
    if (s2 || this.applyDeoptimizations(), i2.hasEffectsAsAssignmentTarget(e3, false) || n2.hasEffects(e3))
      return true;
    const { brokenFlow: r2, ignore: o2 } = e3, { breaks: a2, continues: l2 } = o2;
    return o2.breaks = true, o2.continues = true, !!t2.hasEffects(e3) || (o2.breaks = a2, o2.continues = l2, e3.brokenFlow = r2, false);
  }
  include(e3, t2) {
    const { body: s2, deoptimized: i2, left: n2, right: r2 } = this;
    i2 || this.applyDeoptimizations(), this.included = true, n2.includeAsAssignmentTarget(e3, t2 || true, false), r2.include(e3, t2);
    const { brokenFlow: o2 } = e3;
    s2.include(e3, t2, { asSingleStatement: true }), e3.brokenFlow = o2;
  }
  initialise() {
    this.left.setAssignedValue(ne);
  }
  render(e3, t2) {
    this.left.render(e3, t2, ti), this.right.render(e3, t2, ti), 110 === e3.original.charCodeAt(this.right.start - 1) && e3.prependLeft(this.right.start, " "), this.body.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(H), this.context.requestTreeshakingPass();
  }
}, ForOfStatement: class extends es {
  createScope(e3) {
    this.scope = new hi(e3);
  }
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), true;
  }
  include(e3, t2) {
    const { body: s2, deoptimized: i2, left: n2, right: r2 } = this;
    i2 || this.applyDeoptimizations(), this.included = true, n2.includeAsAssignmentTarget(e3, t2 || true, false), r2.include(e3, t2);
    const { brokenFlow: o2 } = e3;
    s2.include(e3, t2, { asSingleStatement: true }), e3.brokenFlow = o2;
  }
  initialise() {
    this.left.setAssignedValue(ne);
  }
  render(e3, t2) {
    this.left.render(e3, t2, ti), this.right.render(e3, t2, ti), 102 === e3.original.charCodeAt(this.right.start - 1) && e3.prependLeft(this.right.start, " "), this.body.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(H), this.context.requestTreeshakingPass();
  }
}, ForStatement: class extends es {
  createScope(e3) {
    this.scope = new hi(e3);
  }
  hasEffects(e3) {
    var _a2, _b, _c2;
    if (((_a2 = this.init) == null ? void 0 : _a2.hasEffects(e3)) || ((_b = this.test) == null ? void 0 : _b.hasEffects(e3)) || ((_c2 = this.update) == null ? void 0 : _c2.hasEffects(e3)))
      return true;
    const { brokenFlow: t2, ignore: s2 } = e3, { breaks: i2, continues: n2 } = s2;
    return s2.breaks = true, s2.continues = true, !!this.body.hasEffects(e3) || (s2.breaks = i2, s2.continues = n2, e3.brokenFlow = t2, false);
  }
  include(e3, t2) {
    var _a2, _b, _c2;
    this.included = true, (_a2 = this.init) == null ? void 0 : _a2.include(e3, t2, { asSingleStatement: true }), (_b = this.test) == null ? void 0 : _b.include(e3, t2);
    const { brokenFlow: s2 } = e3;
    (_c2 = this.update) == null ? void 0 : _c2.include(e3, t2), this.body.include(e3, t2, { asSingleStatement: true }), e3.brokenFlow = s2;
  }
  render(e3, t2) {
    var _a2, _b, _c2;
    (_a2 = this.init) == null ? void 0 : _a2.render(e3, t2, ti), (_b = this.test) == null ? void 0 : _b.render(e3, t2, ti), (_c2 = this.update) == null ? void 0 : _c2.render(e3, t2, ti), this.body.render(e3, t2);
  }
}, FunctionDeclaration: zi, FunctionExpression: class extends Si {
  render(e3, t2, { renderedSurroundingElement: s2 } = de) {
    super.render(e3, t2), s2 === Vt && (e3.appendRight(this.start, "("), e3.prependLeft(this.end, ")"));
  }
}, Identifier: Qs, IfStatement: qi, ImportAttribute: class extends es {
}, ImportDeclaration: Hi, ImportDefaultSpecifier: Ki, ImportExpression: class extends es {
  constructor() {
    super(...arguments), this.inlineNamespace = null, this.assertions = null, this.mechanism = null, this.namespaceExportName = void 0, this.resolution = null, this.resolutionString = null;
  }
  bind() {
    this.source.bind();
  }
  hasEffects() {
    return true;
  }
  include(e3, t2) {
    this.included || (this.included = true, this.context.includeDynamicImport(this), this.scope.addAccessedDynamicImport(this)), this.source.include(e3, t2);
  }
  initialise() {
    this.context.addDynamicImport(this);
  }
  parseNode(e3) {
    super.parseNode(e3, ["source"]);
  }
  render(e3, t2) {
    const { snippets: { _: s2, getDirectReturnFunction: i2, getObject: n2, getPropertyAccess: r2 } } = t2;
    if (this.inlineNamespace) {
      const [t3, s3] = i2([], { functionReturn: true, lineBreakIndent: null, name: null });
      e3.overwrite(this.start, this.end, `Promise.resolve().then(${t3}${this.inlineNamespace.getName(r2)}${s3})`);
    } else {
      if (this.mechanism && (e3.overwrite(this.start, si(e3.original, "(", this.start + 6) + 1, this.mechanism.left), e3.overwrite(this.end - 1, this.end, this.mechanism.right)), this.resolutionString) {
        if (e3.overwrite(this.source.start, this.source.end, this.resolutionString), this.namespaceExportName) {
          const [t3, s3] = i2(["n"], { functionReturn: true, lineBreakIndent: null, name: null });
          e3.prependLeft(this.end, `.then(${t3}n.${this.namespaceExportName}${s3})`);
        }
      } else
        this.source.render(e3, t2);
      true !== this.assertions && (this.arguments && e3.overwrite(this.source.end, this.end - 1, "", { contentOnly: true }), this.assertions && e3.appendLeft(this.end - 1, `,${s2}${n2([["assert", this.assertions]], { lineBreakIndent: null })}`));
    }
  }
  setExternalResolution(e3, t2, s2, i2, n2, r2, o2, a2, l2) {
    const { format: h2 } = s2;
    this.inlineNamespace = null, this.resolution = t2, this.resolutionString = o2, this.namespaceExportName = a2, this.assertions = l2;
    const c2 = [...An[h2] || []];
    let u2;
    ({ helper: u2, mechanism: this.mechanism } = this.getDynamicImportMechanismAndHelper(t2, e3, s2, i2, n2)), u2 && c2.push(u2), c2.length > 0 && this.scope.addAccessedGlobals(c2, r2);
  }
  setInternalResolution(e3) {
    this.inlineNamespace = e3;
  }
  applyDeoptimizations() {
  }
  getDynamicImportMechanismAndHelper(e3, t2, { compact: s2, dynamicImportFunction: i2, dynamicImportInCjs: n2, format: r2, generatedCode: { arrowFunctions: o2 }, interop: a2 }, { _: l2, getDirectReturnFunction: h2, getDirectReturnIifeLeft: c2 }, u2) {
    const d2 = u2.hookFirstSync("renderDynamicImport", [{ customResolution: "string" == typeof this.resolution ? this.resolution : null, format: r2, moduleId: this.context.module.id, targetModuleId: this.resolution && "string" != typeof this.resolution ? this.resolution.id : null }]);
    if (d2)
      return { helper: null, mechanism: d2 };
    const p2 = !this.resolution || "string" == typeof this.resolution;
    switch (r2) {
      case "cjs": {
        if (n2 && (!e3 || "string" == typeof e3 || e3 instanceof ct))
          return { helper: null, mechanism: null };
        const s3 = kn(e3, t2, a2);
        let i3 = "require(", r3 = ")";
        s3 && (i3 = `/*#__PURE__*/${s3}(${i3}`, r3 += ")");
        const [l3, u3] = h2([], { functionReturn: true, lineBreakIndent: null, name: null });
        return i3 = `Promise.resolve().then(${l3}${i3}`, r3 += `${u3})`, !o2 && p2 && (i3 = c2(["t"], `${i3}t${r3}`, { needsArrowReturnParens: false, needsWrappedFunction: true }), r3 = ")"), { helper: s3, mechanism: { left: i3, right: r3 } };
      }
      case "amd": {
        const i3 = s2 ? "c" : "resolve", n3 = s2 ? "e" : "reject", r3 = kn(e3, t2, a2), [u3, d3] = h2(["m"], { functionReturn: false, lineBreakIndent: null, name: null }), f2 = r3 ? `${u3}${i3}(/*#__PURE__*/${r3}(m))${d3}` : i3, [m2, g2] = h2([i3, n3], { functionReturn: false, lineBreakIndent: null, name: null });
        let y2 = `new Promise(${m2}require([`, x2 = `],${l2}${f2},${l2}${n3})${g2})`;
        return !o2 && p2 && (y2 = c2(["t"], `${y2}t${x2}`, { needsArrowReturnParens: false, needsWrappedFunction: true }), x2 = ")"), { helper: r3, mechanism: { left: y2, right: x2 } };
      }
      case "system":
        return { helper: null, mechanism: { left: "module.import(", right: ")" } };
      case "es":
        if (i2)
          return { helper: null, mechanism: { left: `${i2}(`, right: ")" } };
    }
    return { helper: null, mechanism: null };
  }
}, ImportNamespaceSpecifier: wn, ImportSpecifier: class extends es {
  applyDeoptimizations() {
  }
}, LabeledStatement: class extends es {
  hasEffects(e3) {
    const t2 = e3.brokenFlow;
    return e3.ignore.labels.add(this.label.name), !!this.body.hasEffects(e3) || (e3.ignore.labels.delete(this.label.name), e3.includedLabels.has(this.label.name) && (e3.includedLabels.delete(this.label.name), e3.brokenFlow = t2), false);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = e3.brokenFlow;
    this.body.include(e3, t2), (t2 || e3.includedLabels.has(this.label.name)) && (this.label.include(), e3.includedLabels.delete(this.label.name), e3.brokenFlow = s2);
  }
  render(e3, t2) {
    this.label.included ? this.label.render(e3, t2) : e3.remove(this.start, ni(e3.original, si(e3.original, ":", this.label.end) + 1)), this.body.render(e3, t2);
  }
}, Literal: wi, LogicalExpression: class extends es {
  constructor() {
    super(...arguments), this.expressionsToBeDeoptimized = [], this.isBranchResolutionAnalysed = false, this.usedBranch = null;
  }
  deoptimizeCache() {
    if (this.usedBranch) {
      const e3 = this.usedBranch === this.left ? this.right : this.left;
      this.usedBranch = null, e3.deoptimizePath(K);
      for (const e4 of this.expressionsToBeDeoptimized)
        e4.deoptimizeCache();
      this.context.requestTreeshakingPass();
    }
  }
  deoptimizePath(e3) {
    const t2 = this.getUsedBranch();
    t2 ? t2.deoptimizePath(e3) : (this.left.deoptimizePath(e3), this.right.deoptimizePath(e3));
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.left.deoptimizeThisOnInteractionAtPath(e3, t2, s2), this.right.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? (this.expressionsToBeDeoptimized.push(s2), i2.getLiteralValueAtPath(e3, t2, s2)) : te;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const n2 = this.getUsedBranch();
    return n2 ? (this.expressionsToBeDeoptimized.push(i2), n2.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)) : [new Bi([this.left.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0], this.right.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0]]), false];
  }
  hasEffects(e3) {
    return !!this.left.hasEffects(e3) || this.getUsedBranch() !== this.left && this.right.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? i2.hasEffectsOnInteractionAtPath(e3, t2, s2) : this.left.hasEffectsOnInteractionAtPath(e3, t2, s2) || this.right.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = this.getUsedBranch();
    t2 || s2 === this.right && this.left.shouldBeIncluded(e3) || !s2 ? (this.left.include(e3, t2), this.right.include(e3, t2)) : s2.include(e3, t2);
  }
  render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2, renderedSurroundingElement: r2 } = de) {
    if (this.left.included && this.right.included)
      this.left.render(e3, t2, { preventASI: i2, renderedSurroundingElement: r2 }), this.right.render(e3, t2);
    else {
      const o2 = si(e3.original, this.operator, this.left.end);
      if (this.right.included) {
        const t3 = ni(e3.original, o2 + 2);
        e3.remove(this.start, t3), i2 && li(e3, t3, this.right.start);
      } else
        e3.remove(o2, this.end);
      ei(this, e3), this.getUsedBranch().render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2 || this.parent.type, renderedSurroundingElement: r2 || this.parent.type });
    }
  }
  getUsedBranch() {
    if (!this.isBranchResolutionAnalysed) {
      this.isBranchResolutionAnalysed = true;
      const e3 = this.left.getLiteralValueAtPath(H, Z, this);
      if ("symbol" == typeof e3)
        return null;
      this.usedBranch = "||" === this.operator && e3 || "&&" === this.operator && !e3 || "??" === this.operator && null != e3 ? this.left : this.right;
    }
    return this.usedBranch;
  }
}, MemberExpression: Ci, MetaProperty: class extends es {
  constructor() {
    super(...arguments), this.metaProperty = null, this.preliminaryChunkId = null, this.referenceId = null;
  }
  getReferencedFileName(e3) {
    const { meta: { name: t2 }, metaProperty: s2 } = this;
    return t2 === Pn && (s2 == null ? void 0 : s2.startsWith(In)) ? e3.getFileName(s2.slice(In.length)) : null;
  }
  hasEffects() {
    return false;
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 1 || 0 !== t2;
  }
  include() {
    if (!this.included && (this.included = true, this.meta.name === Pn)) {
      this.context.addImportMeta(this);
      const e3 = this.parent, t2 = this.metaProperty = e3 instanceof Ci && "string" == typeof e3.propertyKey ? e3.propertyKey : null;
      (t2 == null ? void 0 : t2.startsWith(In)) && (this.referenceId = t2.slice(In.length));
    }
  }
  render(e3, { format: t2, pluginDriver: s2, snippets: i2 }) {
    var _a2;
    const { context: { module: { id: n2 } }, meta: { name: r2 }, metaProperty: o2, parent: a2, preliminaryChunkId: l2, referenceId: h2, start: c2, end: u2 } = this;
    if (r2 !== Pn)
      return;
    const d2 = l2;
    if (h2) {
      const i3 = s2.getFileName(h2), r3 = A(C(I(d2), i3)), o3 = s2.hookFirstSync("resolveFileUrl", [{ chunkId: d2, fileName: i3, format: t2, moduleId: n2, referenceId: h2, relativePath: r3 }]) || Dn[t2](r3);
      return void e3.overwrite(a2.start, a2.end, o3, { contentOnly: true });
    }
    const p2 = s2.hookFirstSync("resolveImportMeta", [o2, { chunkId: d2, format: t2, moduleId: n2 }]) || ((_a2 = Ln[t2]) == null ? void 0 : _a2.call(Ln, o2, { chunkId: d2, snippets: i2 }));
    "string" == typeof p2 && (a2 instanceof Ci ? e3.overwrite(a2.start, a2.end, p2, { contentOnly: true }) : e3.overwrite(c2, u2, p2, { contentOnly: true }));
  }
  setResolution(e3, t2, s2) {
    var _a2;
    this.preliminaryChunkId = s2;
    const i2 = (((_a2 = this.metaProperty) == null ? void 0 : _a2.startsWith(In)) ? $n : Cn)[e3];
    i2.length > 0 && this.scope.addAccessedGlobals(i2, t2);
  }
}, MethodDefinition: Mi, NewExpression: class extends es {
  hasEffects(e3) {
    try {
      for (const t2 of this.arguments)
        if (t2.hasEffects(e3))
          return true;
      return (!this.context.options.treeshake.annotations || !this.annotations) && (this.callee.hasEffects(e3) || this.callee.hasEffectsOnInteractionAtPath(H, this.interaction, e3));
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 0 || 0 !== t2;
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? super.include(e3, t2) : (this.included = true, this.callee.include(e3, false)), this.callee.includeCallArguments(e3, this.arguments);
  }
  initialise() {
    this.interaction = { args: this.arguments, thisArg: null, type: 2, withNew: true };
  }
  render(e3, t2) {
    this.callee.render(e3, t2), Ai(e3, t2, this);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e3 of this.arguments)
      e3.deoptimizePath(K);
    this.context.requestTreeshakingPass();
  }
}, ObjectExpression: class extends es {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  deoptimizeCache() {
    this.getObjectEntity().deoptimizeAllProperties();
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.getObjectEntity().deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  render(e3, t2, { renderedSurroundingElement: s2 } = de) {
    super.render(e3, t2), s2 !== Vt && s2 !== Ot || (e3.appendRight(this.start, "("), e3.prependLeft(this.end, ")"));
  }
  applyDeoptimizations() {
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    let e3 = us;
    const t2 = [];
    for (const s2 of this.properties) {
      if (s2 instanceof ts) {
        t2.push({ key: U, kind: "init", property: s2 });
        continue;
      }
      let i2;
      if (s2.computed) {
        const e4 = s2.key.getLiteralValueAtPath(H, Z, this);
        if ("symbol" == typeof e4) {
          t2.push({ key: U, kind: s2.kind, property: s2 });
          continue;
        }
        i2 = String(e4);
      } else if (i2 = s2.key instanceof Qs ? s2.key.name : String(s2.key.value), "__proto__" === i2 && "init" === s2.kind) {
        e3 = s2.value instanceof wi && null === s2.value.value ? null : s2.value;
        continue;
      }
      t2.push({ key: i2, kind: s2.kind, property: s2 });
    }
    return this.objectEntity = new ls(t2, e3);
  }
}, ObjectPattern: xi, PrivateIdentifier: class extends es {
}, Program: Bn, Property: class extends Ri {
  constructor() {
    super(...arguments), this.declarationInit = null;
  }
  declare(e3, t2) {
    return this.declarationInit = t2, this.value.declare(e3, ne);
  }
  hasEffects(e3) {
    this.deoptimized || this.applyDeoptimizations();
    const t2 = this.context.options.treeshake.propertyReadSideEffects;
    return "ObjectPattern" === this.parent.type && "always" === t2 || this.key.hasEffects(e3) || this.value.hasEffects(e3);
  }
  markDeclarationReached() {
    this.value.markDeclarationReached();
  }
  render(e3, t2) {
    this.shorthand || this.key.render(e3, t2), this.value.render(e3, t2, { isShorthandProperty: this.shorthand });
  }
  applyDeoptimizations() {
    this.deoptimized = true, null !== this.declarationInit && (this.declarationInit.deoptimizePath([U, U]), this.context.requestTreeshakingPass());
  }
}, PropertyDefinition: class extends es {
  deoptimizePath(e3) {
    var _a2;
    (_a2 = this.value) == null ? void 0 : _a2.deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    (_a2 = this.value) == null ? void 0 : _a2.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.value ? this.value.getLiteralValueAtPath(e3, t2, s2) : te;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.value ? this.value.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : re;
  }
  hasEffects(e3) {
    var _a2;
    return this.key.hasEffects(e3) || this.static && !!((_a2 = this.value) == null ? void 0 : _a2.hasEffects(e3));
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return !this.value || this.value.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  applyDeoptimizations() {
  }
}, RestElement: di, ReturnStatement: class extends es {
  hasEffects(e3) {
    var _a2;
    return !(e3.ignore.returnYield && !((_a2 = this.argument) == null ? void 0 : _a2.hasEffects(e3))) || (e3.brokenFlow = 2, false);
  }
  include(e3, t2) {
    var _a2;
    this.included = true, (_a2 = this.argument) == null ? void 0 : _a2.include(e3, t2), e3.brokenFlow = 2;
  }
  initialise() {
    this.scope.addReturnExpression(this.argument || ne);
  }
  render(e3, t2) {
    this.argument && (this.argument.render(e3, t2, { preventASI: true }), this.argument.start === this.start + 6 && e3.prependLeft(this.start + 6, " "));
  }
}, SequenceExpression: class extends es {
  deoptimizePath(e3) {
    this.expressions[this.expressions.length - 1].deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.expressions[this.expressions.length - 1].deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.expressions[this.expressions.length - 1].getLiteralValueAtPath(e3, t2, s2);
  }
  hasEffects(e3) {
    for (const t2 of this.expressions)
      if (t2.hasEffects(e3))
        return true;
    return false;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.expressions[this.expressions.length - 1].hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = this.expressions[this.expressions.length - 1];
    for (const i2 of this.expressions)
      (t2 || i2 === s2 && !(this.parent instanceof ci) || i2.shouldBeIncluded(e3)) && i2.include(e3, t2);
  }
  render(e3, t2, { renderedParentType: s2, isCalleeOfRenderedParent: i2, preventASI: n2 } = de) {
    let r2 = 0, o2 = null;
    const a2 = this.expressions[this.expressions.length - 1];
    for (const { node: l2, separator: h2, start: c2, end: u2 } of ai(this.expressions, e3, this.start, this.end))
      if (l2.included)
        if (r2++, o2 = h2, 1 === r2 && n2 && li(e3, c2, l2.start), 1 === r2) {
          const n3 = s2 || this.parent.type;
          l2.render(e3, t2, { isCalleeOfRenderedParent: i2 && l2 === a2, renderedParentType: n3, renderedSurroundingElement: n3 });
        } else
          l2.render(e3, t2);
      else
        Zs(l2, e3, c2, u2);
    o2 && e3.remove(o2, this.end);
  }
}, SpreadElement: ts, StaticBlock: class extends es {
  createScope(e3) {
    this.scope = new hi(e3);
  }
  hasEffects(e3) {
    for (const t2 of this.body)
      if (t2.hasEffects(e3))
        return true;
    return false;
  }
  include(e3, t2) {
    this.included = true;
    for (const s2 of this.body)
      (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
  }
  render(e3, t2) {
    this.body.length > 0 ? oi(this.body, e3, this.start + 1, this.end - 1, t2) : super.render(e3, t2);
  }
}, Super: class extends es {
  bind() {
    this.variable = this.scope.findVariable("this");
  }
  deoptimizePath(e3) {
    this.variable.deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.variable.deoptimizeThisOnInteractionAtPath(e3, t2, s2);
  }
  include() {
    this.included || (this.included = true, this.context.includeVariableInModule(this.variable));
  }
}, SwitchCase: Fn, SwitchStatement: class extends es {
  createScope(e3) {
    this.scope = new hi(e3);
  }
  hasEffects(e3) {
    if (this.discriminant.hasEffects(e3))
      return true;
    const { brokenFlow: t2, ignore: s2 } = e3, { breaks: i2 } = s2;
    let n2 = 1 / 0;
    s2.breaks = true;
    for (const s3 of this.cases) {
      if (s3.hasEffects(e3))
        return true;
      n2 = e3.brokenFlow < n2 ? e3.brokenFlow : n2, e3.brokenFlow = t2;
    }
    return null !== this.defaultCase && 1 !== n2 && (e3.brokenFlow = n2), s2.breaks = i2, false;
  }
  include(e3, t2) {
    this.included = true, this.discriminant.include(e3, t2);
    const { brokenFlow: s2 } = e3;
    let i2 = 1 / 0, n2 = t2 || null !== this.defaultCase && this.defaultCase < this.cases.length - 1;
    for (let r2 = this.cases.length - 1; r2 >= 0; r2--) {
      const o2 = this.cases[r2];
      if (o2.included && (n2 = true), !n2) {
        const e4 = ft();
        e4.ignore.breaks = true, n2 = o2.hasEffects(e4);
      }
      n2 ? (o2.include(e3, t2), i2 = i2 < e3.brokenFlow ? i2 : e3.brokenFlow, e3.brokenFlow = s2) : i2 = s2;
    }
    n2 && null !== this.defaultCase && 1 !== i2 && (e3.brokenFlow = i2);
  }
  initialise() {
    for (let e3 = 0; e3 < this.cases.length; e3++)
      if (null === this.cases[e3].test)
        return void (this.defaultCase = e3);
    this.defaultCase = null;
  }
  render(e3, t2) {
    this.discriminant.render(e3, t2), this.cases.length > 0 && oi(this.cases, e3, this.cases[0].start, this.end - 1, t2);
  }
}, TaggedTemplateExpression: class extends Ni {
  bind() {
    if (super.bind(), this.tag.type === Bt) {
      const e3 = this.tag.name;
      this.scope.findVariable(e3).isNamespace && this.context.warn(Ue(e3), this.start);
    }
  }
  hasEffects(e3) {
    try {
      for (const t2 of this.quasi.expressions)
        if (t2.hasEffects(e3))
          return true;
      return this.tag.hasEffects(e3) || this.tag.hasEffectsOnInteractionAtPath(H, this.interaction, e3);
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? super.include(e3, t2) : (this.included = true, this.tag.include(e3, t2), this.quasi.include(e3, t2)), this.tag.includeCallArguments(e3, this.interaction.args);
    const [s2] = this.getReturnExpression();
    s2.included || s2.include(e3, false);
  }
  initialise() {
    this.interaction = { args: [ne, ...this.quasi.expressions], thisArg: this.tag instanceof Ci && !this.tag.variable ? this.tag.object : null, type: 2, withNew: false };
  }
  render(e3, t2) {
    this.tag.render(e3, t2, { isCalleeOfRenderedParent: true }), this.quasi.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.interaction.thisArg && this.tag.deoptimizeThisOnInteractionAtPath(this.interaction, H, Z);
    for (const e3 of this.quasi.expressions)
      e3.deoptimizePath(K);
    this.context.requestTreeshakingPass();
  }
  getReturnExpression(e3 = Z) {
    return null === this.returnExpression ? (this.returnExpression = re, this.returnExpression = this.tag.getReturnExpressionWhenCalledAtPath(H, this.interaction, e3, this)) : this.returnExpression;
  }
}, TemplateElement: class extends es {
  bind() {
  }
  hasEffects() {
    return false;
  }
  include() {
    this.included = true;
  }
  parseNode(e3) {
    this.value = e3.value, super.parseNode(e3);
  }
  render() {
  }
}, TemplateLiteral: zn, ThisExpression: class extends es {
  bind() {
    this.variable = this.scope.findVariable("this");
  }
  deoptimizePath(e3) {
    this.variable.deoptimizePath(e3);
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    this.variable.deoptimizeThisOnInteractionAtPath(e3.thisArg === this ? { ...e3, thisArg: this.variable } : e3, t2, s2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === e3.length ? 0 !== t2.type : this.variable.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include() {
    this.included || (this.included = true, this.context.includeVariableInModule(this.variable));
  }
  initialise() {
    this.alias = this.scope.findLexicalBoundary() instanceof Gn ? this.context.moduleContext : null, "undefined" === this.alias && this.context.warn({ code: "THIS_IS_UNDEFINED", message: "The 'this' keyword is equivalent to 'undefined' at the top level of an ES module, and has been rewritten", url: Ee("troubleshooting/#error-this-is-undefined") }, this.start);
  }
  render(e3) {
    null !== this.alias && e3.overwrite(this.start, this.end, this.alias, { contentOnly: false, storeName: true });
  }
}, ThrowStatement: class extends es {
  hasEffects() {
    return true;
  }
  include(e3, t2) {
    this.included = true, this.argument.include(e3, t2), e3.brokenFlow = 2;
  }
  render(e3, t2) {
    this.argument.render(e3, t2, { preventASI: true }), this.argument.start === this.start + 5 && e3.prependLeft(this.start + 5, " ");
  }
}, TryStatement: class extends es {
  constructor() {
    super(...arguments), this.directlyIncluded = false, this.includedLabelsAfterBlock = null;
  }
  hasEffects(e3) {
    var _a2;
    return (this.context.options.treeshake.tryCatchDeoptimization ? this.block.body.length > 0 : this.block.hasEffects(e3)) || !!((_a2 = this.finalizer) == null ? void 0 : _a2.hasEffects(e3));
  }
  include(e3, t2) {
    var _a2, _b;
    const s2 = (_a2 = this.context.options.treeshake) == null ? void 0 : _a2.tryCatchDeoptimization, { brokenFlow: i2, includedLabels: n2 } = e3;
    if (this.directlyIncluded && s2) {
      if (this.includedLabelsAfterBlock)
        for (const e4 of this.includedLabelsAfterBlock)
          n2.add(e4);
    } else
      this.included = true, this.directlyIncluded = true, this.block.include(e3, s2 ? Zt : t2), n2.size > 0 && (this.includedLabelsAfterBlock = [...n2]), e3.brokenFlow = i2;
    null !== this.handler && (this.handler.include(e3, t2), e3.brokenFlow = i2), (_b = this.finalizer) == null ? void 0 : _b.include(e3, t2);
  }
}, UnaryExpression: class extends es {
  getLiteralValueAtPath(e3, t2, s2) {
    if (e3.length > 0)
      return te;
    const i2 = this.argument.getLiteralValueAtPath(H, t2, s2);
    return "symbol" == typeof i2 ? te : Wn[this.operator](i2);
  }
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), !("typeof" === this.operator && this.argument instanceof Qs) && (this.argument.hasEffects(e3) || "delete" === this.operator && this.argument.hasEffectsOnInteractionAtPath(H, ae, e3));
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > ("void" === this.operator ? 0 : 1);
  }
  applyDeoptimizations() {
    this.deoptimized = true, "delete" === this.operator && (this.argument.deoptimizePath(H), this.context.requestTreeshakingPass());
  }
}, UnknownNode: class extends es {
  hasEffects() {
    return true;
  }
  include(e3) {
    super.include(e3, true);
  }
}, UpdateExpression: class extends es {
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), this.argument.hasEffectsAsAssignmentTarget(e3, true);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 1 || 0 !== t2;
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true, this.argument.includeAsAssignmentTarget(e3, t2, true);
  }
  initialise() {
    this.argument.setAssignedValue(ne);
  }
  render(e3, t2) {
    const { exportNamesByVariable: s2, format: i2, snippets: { _: n2 } } = t2;
    if (this.argument.render(e3, t2), "system" === i2) {
      const i3 = this.argument.variable, r2 = s2.get(i3);
      if (r2)
        if (this.prefix)
          1 === r2.length ? gi(i3, this.start, this.end, e3, t2) : yi(i3, this.start, this.end, this.parent.type !== Vt, e3, t2);
        else {
          const s3 = this.operator[0];
          !function(e4, t3, s4, i4, n3, r3, o2) {
            const { _: a2 } = r3.snippets;
            n3.prependRight(t3, `${mi([e4], r3, o2)},${a2}`), i4 && (n3.prependRight(t3, "("), n3.appendLeft(s4, ")"));
          }(i3, this.start, this.end, this.parent.type !== Vt, e3, t2, `${n2}${s3}${n2}1`);
        }
    }
  }
  applyDeoptimizations() {
    if (this.deoptimized = true, this.argument.deoptimizePath(H), this.argument instanceof Qs) {
      this.scope.findVariable(this.argument.name).isReassigned = true;
    }
    this.context.requestTreeshakingPass();
  }
}, VariableDeclaration: Hn, VariableDeclarator: class extends es {
  declareDeclarator(e3) {
    this.id.declare(e3, this.init || gt);
  }
  deoptimizePath(e3) {
    this.id.deoptimizePath(e3);
  }
  hasEffects(e3) {
    var _a2;
    this.deoptimized || this.applyDeoptimizations();
    const t2 = (_a2 = this.init) == null ? void 0 : _a2.hasEffects(e3);
    return this.id.markDeclarationReached(), t2 || this.id.hasEffects(e3);
  }
  include(e3, t2) {
    const { deoptimized: s2, id: i2, init: n2 } = this;
    s2 || this.applyDeoptimizations(), this.included = true, n2 == null ? void 0 : n2.include(e3, t2), i2.markDeclarationReached(), (t2 || i2.shouldBeIncluded(e3)) && i2.include(e3, t2);
  }
  render(e3, t2) {
    const { exportNamesByVariable: s2, snippets: { _: i2, getPropertyAccess: n2 } } = t2, { end: r2, id: o2, init: a2, start: l2 } = this, h2 = o2.included;
    if (h2)
      o2.render(e3, t2);
    else {
      const t3 = si(e3.original, "=", o2.end);
      e3.remove(l2, ni(e3.original, t3 + 1));
    }
    if (a2) {
      if (o2 instanceof Qs && a2 instanceof Vi && !a2.id) {
        o2.variable.getName(n2) !== o2.name && e3.appendLeft(a2.start + 5, ` ${o2.name}`);
      }
      a2.render(e3, t2, h2 ? de : { renderedSurroundingElement: Vt });
    } else
      o2 instanceof Qs && qn(o2.variable, s2) && e3.appendLeft(r2, `${i2}=${i2}void 0`);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    const { id: e3, init: t2 } = this;
    if (t2 && e3 instanceof Qs && t2 instanceof Vi && !t2.id) {
      const { name: s2, variable: i2 } = e3;
      for (const e4 of t2.scope.accessedOutsideVariables.values())
        e4 !== i2 && e4.forbidName(s2);
    }
  }
}, WhileStatement: class extends es {
  hasEffects(e3) {
    if (this.test.hasEffects(e3))
      return true;
    const { brokenFlow: t2, ignore: s2 } = e3, { breaks: i2, continues: n2 } = s2;
    return s2.breaks = true, s2.continues = true, !!this.body.hasEffects(e3) || (s2.breaks = i2, s2.continues = n2, e3.brokenFlow = t2, false);
  }
  include(e3, t2) {
    this.included = true, this.test.include(e3, t2);
    const { brokenFlow: s2 } = e3;
    this.body.include(e3, t2, { asSingleStatement: true }), e3.brokenFlow = s2;
  }
}, YieldExpression: class extends es {
  hasEffects(e3) {
    var _a2;
    return this.deoptimized || this.applyDeoptimizations(), !(e3.ignore.returnYield && !((_a2 = this.argument) == null ? void 0 : _a2.hasEffects(e3)));
  }
  render(e3, t2) {
    this.argument && (this.argument.render(e3, t2, { preventASI: true }), this.argument.start === this.start + 5 && e3.prependLeft(this.start + 5, " "));
  }
} }, Yn = "_missingExportShim";
class Xn extends ce {
  constructor(e3) {
    super(Yn), this.module = e3;
  }
  include() {
    super.include(), this.module.needsExportShim = true;
  }
}
class Qn extends ce {
  constructor(e3) {
    super(e3.getModuleName()), this.memberVariables = null, this.mergedNamespaces = [], this.referencedEarly = false, this.references = [], this.context = e3, this.module = e3.module;
  }
  addReference(e3) {
    this.references.push(e3), this.name = e3.name;
  }
  deoptimizePath(e3) {
    var _a2;
    if (e3.length > 1) {
      const t2 = e3[0];
      "string" == typeof t2 && ((_a2 = this.getMemberVariables()[t2]) == null ? void 0 : _a2.deoptimizePath(e3.slice(1)));
    }
  }
  deoptimizeThisOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    if (t2.length > 1 || 1 === t2.length && 2 === e3.type) {
      const i2 = t2[0];
      "string" == typeof i2 ? (_a2 = this.getMemberVariables()[i2]) == null ? void 0 : _a2.deoptimizeThisOnInteractionAtPath(e3, t2.slice(1), s2) : e3.thisArg.deoptimizePath(K);
    }
  }
  getLiteralValueAtPath(e3) {
    return e3[0] === q ? "Module" : te;
  }
  getMemberVariables() {
    if (this.memberVariables)
      return this.memberVariables;
    const e3 = /* @__PURE__ */ Object.create(null), t2 = [...this.context.getExports(), ...this.context.getReexports()].sort();
    for (const s2 of t2)
      if ("*" !== s2[0] && s2 !== this.module.info.syntheticNamedExports) {
        const t3 = this.context.traceExport(s2);
        t3 && (e3[s2] = t3);
      }
    return this.memberVariables = e3;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const { type: i2 } = t2;
    if (0 === e3.length)
      return true;
    if (1 === e3.length && 2 !== i2)
      return 1 === i2;
    const n2 = e3[0];
    if ("string" != typeof n2)
      return true;
    const r2 = this.getMemberVariables()[n2];
    return !r2 || r2.hasEffectsOnInteractionAtPath(e3.slice(1), t2, s2);
  }
  include() {
    this.included = true, this.context.includeAllExports();
  }
  prepare(e3) {
    this.mergedNamespaces.length > 0 && this.module.scope.addAccessedGlobals([tn], e3);
  }
  renderBlock(e3) {
    const { exportNamesByVariable: t2, format: s2, freeze: i2, indent: n2, namespaceToStringTag: r2, snippets: { _: o2, cnst: a2, getObject: l2, getPropertyAccess: h2, n: c2, s: u2 } } = e3, d2 = this.getMemberVariables(), p2 = Object.entries(d2).map(([e4, t3]) => this.referencedEarly || t3.isReassigned ? [null, `get ${e4}${o2}()${o2}{${o2}return ${t3.getName(h2)}${u2}${o2}}`] : [e4, t3.getName(h2)]);
    p2.unshift([null, `__proto__:${o2}null`]);
    let f2 = l2(p2, { lineBreakIndent: { base: "", t: n2 } });
    if (this.mergedNamespaces.length > 0) {
      const e4 = this.mergedNamespaces.map((e5) => e5.getName(h2));
      f2 = `/*#__PURE__*/${tn}(${f2},${o2}[${e4.join(`,${o2}`)}])`;
    } else
      r2 && (f2 = `/*#__PURE__*/Object.defineProperty(${f2},${o2}Symbol.toStringTag,${o2}${Sn(l2)})`), i2 && (f2 = `/*#__PURE__*/Object.freeze(${f2})`);
    return f2 = `${a2} ${this.getName(h2)}${o2}=${o2}${f2};`, "system" === s2 && t2.has(this) && (f2 += `${c2}${mi([this], e3)};`), f2;
  }
  renderFirst() {
    return this.referencedEarly;
  }
  setMergedNamespaces(e3) {
    this.mergedNamespaces = e3;
    const t2 = this.context.getModuleExecIndex();
    for (const e4 of this.references)
      if (e4.context.getModuleExecIndex() <= t2) {
        this.referencedEarly = true;
        break;
      }
  }
}
Qn.prototype.isNamespace = true;
class Jn extends ce {
  constructor(e3, t2, s2) {
    super(t2), this.baseVariable = null, this.context = e3, this.module = e3.module, this.syntheticNamespace = s2;
  }
  getBaseVariable() {
    if (this.baseVariable)
      return this.baseVariable;
    let e3 = this.syntheticNamespace;
    for (; e3 instanceof Un || e3 instanceof Jn; ) {
      if (e3 instanceof Un) {
        const t2 = e3.getOriginalVariable();
        if (t2 === e3)
          break;
        e3 = t2;
      }
      e3 instanceof Jn && (e3 = e3.syntheticNamespace);
    }
    return this.baseVariable = e3;
  }
  getBaseVariableName() {
    return this.syntheticNamespace.getBaseVariableName();
  }
  getName(e3) {
    return `${this.syntheticNamespace.getName(e3)}${e3(this.name)}`;
  }
  include() {
    this.included = true, this.context.includeVariableInModule(this.syntheticNamespace);
  }
  setRenderNames(e3, t2) {
    super.setRenderNames(e3, t2);
  }
}
var Zn;
function er(e3) {
  return e3.id;
}
!function(e3) {
  e3[e3.LOAD_AND_PARSE = 0] = "LOAD_AND_PARSE", e3[e3.ANALYSE = 1] = "ANALYSE", e3[e3.GENERATE = 2] = "GENERATE";
}(Zn || (Zn = {}));
const tr = (e3) => {
  const t2 = e3.key;
  return t2 && (t2.name || t2.value);
};
function sr(e3, t2) {
  const s2 = Object.keys(e3);
  return s2.length !== Object.keys(t2).length || s2.some((s3) => e3[s3] !== t2[s3]);
}
var ir = "performance" in ("undefined" == typeof globalThis ? "undefined" == typeof window ? {} : window : globalThis) ? performance : { now: () => 0 }, nr = { memoryUsage: () => ({ heapUsed: 0 }) };
const rr = () => {
};
let or = /* @__PURE__ */ new Map();
function ar(e3, t2) {
  switch (t2) {
    case 1:
      return `# ${e3}`;
    case 2:
      return `## ${e3}`;
    case 3:
      return e3;
    default:
      return `${"  ".repeat(t2 - 4)}- ${e3}`;
  }
}
function lr(e3, t2 = 3) {
  e3 = ar(e3, t2);
  const s2 = nr.memoryUsage().heapUsed, i2 = ir.now(), n2 = or.get(e3);
  void 0 === n2 ? or.set(e3, { memory: 0, startMemory: s2, startTime: i2, time: 0, totalMemory: 0 }) : (n2.startMemory = s2, n2.startTime = i2);
}
function hr(e3, t2 = 3) {
  e3 = ar(e3, t2);
  const s2 = or.get(e3);
  if (void 0 !== s2) {
    const e4 = nr.memoryUsage().heapUsed;
    s2.memory += e4 - s2.startMemory, s2.time += ir.now() - s2.startTime, s2.totalMemory = Math.max(s2.totalMemory, e4);
  }
}
function cr() {
  const e3 = {};
  for (const [t2, { memory: s2, time: i2, totalMemory: n2 }] of or)
    e3[t2] = [i2, s2, n2];
  return e3;
}
let ur = rr, dr = rr;
const pr = ["augmentChunkHash", "buildEnd", "buildStart", "generateBundle", "load", "moduleParsed", "options", "outputOptions", "renderChunk", "renderDynamicImport", "renderStart", "resolveDynamicImport", "resolveFileUrl", "resolveId", "resolveImportMeta", "shouldTransformCachedModule", "transform", "writeBundle"];
function fr(e3, t2) {
  for (const s2 of pr)
    if (s2 in e3) {
      let i2 = `plugin ${t2}`;
      e3.name && (i2 += ` (${e3.name})`), i2 += ` - ${s2}`;
      const n2 = function(...e4) {
        ur(i2, 4);
        const t3 = r2.apply(this, e4);
        return dr(i2, 4), t3;
      };
      let r2;
      "function" == typeof e3[s2].handler ? (r2 = e3[s2].handler, e3[s2].handler = n2) : (r2 = e3[s2], e3[s2] = n2);
    }
  return e3;
}
function mr(e3) {
  e3.isExecuted = true;
  const t2 = [e3], s2 = /* @__PURE__ */ new Set();
  for (const e4 of t2)
    for (const i2 of [...e4.dependencies, ...e4.implicitlyLoadedBefore])
      i2 instanceof ct || i2.isExecuted || !i2.info.moduleSideEffects && !e4.implicitlyLoadedBefore.has(i2) || s2.has(i2.id) || (i2.isExecuted = true, s2.add(i2.id), t2.push(i2));
}
const gr = { identifier: null, localName: Yn };
function yr(e3, t2, s2, i2, n2 = /* @__PURE__ */ new Map()) {
  const r2 = n2.get(t2);
  if (r2) {
    if (r2.has(e3))
      return i2 ? [null] : _e((o2 = t2, { code: "CIRCULAR_REEXPORT", exporter: a2 = e3.id, message: `"${o2}" cannot be exported from "${O(a2)}" as it is a reexport that references itself.` }));
    r2.add(e3);
  } else
    n2.set(t2, /* @__PURE__ */ new Set([e3]));
  var o2, a2;
  return e3.getVariableForExportName(t2, { importerForSideEffects: s2, isExportAllSearch: i2, searchedNamesAndModules: n2 });
}
class xr {
  constructor(e3, t2, s2, i2, n2, r2, o2, a2) {
    this.graph = e3, this.id = t2, this.options = s2, this.alternativeReexportModules = /* @__PURE__ */ new Map(), this.chunkFileNames = /* @__PURE__ */ new Set(), this.chunkNames = [], this.cycles = /* @__PURE__ */ new Set(), this.dependencies = /* @__PURE__ */ new Set(), this.dynamicDependencies = /* @__PURE__ */ new Set(), this.dynamicImporters = [], this.dynamicImports = [], this.execIndex = 1 / 0, this.implicitlyLoadedAfter = /* @__PURE__ */ new Set(), this.implicitlyLoadedBefore = /* @__PURE__ */ new Set(), this.importDescriptions = /* @__PURE__ */ new Map(), this.importMetas = [], this.importedFromNotTreeshaken = false, this.importers = [], this.includedDynamicImporters = [], this.includedImports = /* @__PURE__ */ new Set(), this.isExecuted = false, this.isUserDefinedEntryPoint = false, this.needsExportShim = false, this.sideEffectDependenciesByVariable = /* @__PURE__ */ new Map(), this.sourcesWithAssertions = /* @__PURE__ */ new Map(), this.allExportNames = null, this.ast = null, this.exportAllModules = [], this.exportAllSources = /* @__PURE__ */ new Set(), this.exportNamesByVariable = null, this.exportShimVariable = new Xn(this), this.exports = /* @__PURE__ */ new Map(), this.namespaceReexportsByName = /* @__PURE__ */ new Map(), this.reexportDescriptions = /* @__PURE__ */ new Map(), this.relevantDependencies = null, this.syntheticExports = /* @__PURE__ */ new Map(), this.syntheticNamespace = null, this.transformDependencies = [], this.transitiveReexports = null, this.excludeFromSourcemap = /\0/.test(t2), this.context = s2.moduleContext(t2), this.preserveSignature = this.options.preserveEntrySignatures;
    const l2 = this, { dynamicImports: h2, dynamicImporters: c2, exportAllSources: u2, exports: d2, implicitlyLoadedAfter: p2, implicitlyLoadedBefore: f2, importers: m2, reexportDescriptions: g2, sourcesWithAssertions: y2 } = this;
    this.info = { assertions: a2, ast: null, code: null, get dynamicallyImportedIdResolutions() {
      return h2.map(({ argument: e4 }) => "string" == typeof e4 && l2.resolvedIds[e4]).filter(Boolean);
    }, get dynamicallyImportedIds() {
      return h2.map(({ id: e4 }) => e4).filter((e4) => null != e4);
    }, get dynamicImporters() {
      return c2.sort();
    }, get exportedBindings() {
      const e4 = { ".": [...d2.keys()] };
      for (const [t3, { source: s3 }] of g2)
        (e4[s3] ?? (e4[s3] = [])).push(t3);
      for (const t3 of u2)
        (e4[t3] ?? (e4[t3] = [])).push("*");
      return e4;
    }, get exports() {
      return [...d2.keys(), ...g2.keys(), ...[...u2].map(() => "*")];
    }, get hasDefaultExport() {
      return l2.ast ? l2.exports.has("default") || g2.has("default") : null;
    }, get hasModuleSideEffects() {
      return nt("Accessing ModuleInfo.hasModuleSideEffects from plugins is deprecated. Please use ModuleInfo.moduleSideEffects instead.", Ne, true, s2), this.moduleSideEffects;
    }, id: t2, get implicitlyLoadedAfterOneOf() {
      return Array.from(p2, er).sort();
    }, get implicitlyLoadedBefore() {
      return Array.from(f2, er).sort();
    }, get importedIdResolutions() {
      return Array.from(y2.keys(), (e4) => l2.resolvedIds[e4]).filter(Boolean);
    }, get importedIds() {
      return Array.from(y2.keys(), (e4) => {
        var _a2;
        return (_a2 = l2.resolvedIds[e4]) == null ? void 0 : _a2.id;
      }).filter(Boolean);
    }, get importers() {
      return m2.sort();
    }, isEntry: i2, isExternal: false, get isIncluded() {
      return e3.phase !== Zn.GENERATE ? null : l2.isIncluded();
    }, meta: { ...o2 }, moduleSideEffects: n2, syntheticNamedExports: r2 }, Object.defineProperty(this.info, "hasModuleSideEffects", { enumerable: false });
  }
  basename() {
    const e3 = w(this.id), t2 = P(this.id);
    return ht(t2 ? e3.slice(0, -t2.length) : e3);
  }
  bindReferences() {
    this.ast.bind();
  }
  error(e3, t2) {
    return this.addLocationToLogProps(e3, t2), _e(e3);
  }
  getAllExportNames() {
    if (this.allExportNames)
      return this.allExportNames;
    this.allExportNames = /* @__PURE__ */ new Set([...this.exports.keys(), ...this.reexportDescriptions.keys()]);
    for (const e3 of this.exportAllModules)
      if (e3 instanceof ct)
        this.allExportNames.add(`*${e3.id}`);
      else
        for (const t2 of e3.getAllExportNames())
          "default" !== t2 && this.allExportNames.add(t2);
    return "string" == typeof this.info.syntheticNamedExports && this.allExportNames.delete(this.info.syntheticNamedExports), this.allExportNames;
  }
  getDependenciesToBeIncluded() {
    if (this.relevantDependencies)
      return this.relevantDependencies;
    this.relevantDependencies = /* @__PURE__ */ new Set();
    const e3 = /* @__PURE__ */ new Set(), t2 = /* @__PURE__ */ new Set(), s2 = new Set(this.includedImports);
    if (this.info.isEntry || this.includedDynamicImporters.length > 0 || this.namespace.included || this.implicitlyLoadedAfter.size > 0)
      for (const e4 of [...this.getReexports(), ...this.getExports()]) {
        const [t3] = this.getVariableForExportName(e4);
        t3 && s2.add(t3);
      }
    for (let i2 of s2) {
      const s3 = this.sideEffectDependenciesByVariable.get(i2);
      if (s3)
        for (const e4 of s3)
          t2.add(e4);
      i2 instanceof Jn ? i2 = i2.getBaseVariable() : i2 instanceof Un && (i2 = i2.getOriginalVariable()), e3.add(i2.module);
    }
    if (this.options.treeshake && "no-treeshake" !== this.info.moduleSideEffects)
      this.addRelevantSideEffectDependencies(this.relevantDependencies, e3, t2);
    else
      for (const e4 of this.dependencies)
        this.relevantDependencies.add(e4);
    for (const t3 of e3)
      this.relevantDependencies.add(t3);
    return this.relevantDependencies;
  }
  getExportNamesByVariable() {
    if (this.exportNamesByVariable)
      return this.exportNamesByVariable;
    const e3 = /* @__PURE__ */ new Map();
    for (const t2 of this.getAllExportNames()) {
      let [s2] = this.getVariableForExportName(t2);
      if (s2 instanceof Un && (s2 = s2.getOriginalVariable()), !s2 || !(s2.included || s2 instanceof ue))
        continue;
      const i2 = e3.get(s2);
      i2 ? i2.push(t2) : e3.set(s2, [t2]);
    }
    return this.exportNamesByVariable = e3;
  }
  getExports() {
    return [...this.exports.keys()];
  }
  getReexports() {
    if (this.transitiveReexports)
      return this.transitiveReexports;
    this.transitiveReexports = [];
    const e3 = new Set(this.reexportDescriptions.keys());
    for (const t2 of this.exportAllModules)
      if (t2 instanceof ct)
        e3.add(`*${t2.id}`);
      else
        for (const s2 of [...t2.getReexports(), ...t2.getExports()])
          "default" !== s2 && e3.add(s2);
    return this.transitiveReexports = [...e3];
  }
  getRenderedExports() {
    const e3 = [], t2 = [];
    for (const s2 of this.exports.keys()) {
      const [i2] = this.getVariableForExportName(s2);
      (i2 && i2.included ? e3 : t2).push(s2);
    }
    return { removedExports: t2, renderedExports: e3 };
  }
  getSyntheticNamespace() {
    return null === this.syntheticNamespace && (this.syntheticNamespace = void 0, [this.syntheticNamespace] = this.getVariableForExportName("string" == typeof this.info.syntheticNamedExports ? this.info.syntheticNamedExports : "default", { onlyExplicit: true })), this.syntheticNamespace ? this.syntheticNamespace : _e((e3 = this.id, t2 = this.info.syntheticNamedExports, { code: "SYNTHETIC_NAMED_EXPORTS_NEED_NAMESPACE_EXPORT", exporter: e3, message: `Module "${O(e3)}" that is marked with \`syntheticNamedExports: ${JSON.stringify(t2)}\` needs ${"string" == typeof t2 && "default" !== t2 ? `an explicit export named "${t2}"` : "a default export"} that does not reexport an unresolved named export of the same module.` }));
    var e3, t2;
  }
  getVariableForExportName(e3, { importerForSideEffects: t2, isExportAllSearch: s2, onlyExplicit: i2, searchedNamesAndModules: n2 } = pe) {
    if ("*" === e3[0]) {
      if (1 === e3.length)
        return [this.namespace];
      return this.graph.modulesById.get(e3.slice(1)).getVariableForExportName("*");
    }
    const r2 = this.reexportDescriptions.get(e3);
    if (r2) {
      const [e4] = yr(r2.module, r2.localName, t2, false, n2);
      return e4 ? (t2 && (br(e4, t2, this), this.info.moduleSideEffects && F(t2.sideEffectDependenciesByVariable, e4, z).add(this)), [e4]) : this.error(Qe(r2.localName, this.id, r2.module.id), r2.start);
    }
    const o2 = this.exports.get(e3);
    if (o2) {
      if (o2 === gr)
        return [this.exportShimVariable];
      const e4 = o2.localName, s3 = this.traceVariable(e4, { importerForSideEffects: t2, searchedNamesAndModules: n2 });
      return t2 && (br(s3, t2, this), F(t2.sideEffectDependenciesByVariable, s3, z).add(this)), [s3];
    }
    if (i2)
      return [null];
    if ("default" !== e3) {
      const s3 = this.namespaceReexportsByName.get(e3) ?? this.getVariableFromNamespaceReexports(e3, t2, n2);
      if (this.namespaceReexportsByName.set(e3, s3), s3[0])
        return s3;
    }
    return this.info.syntheticNamedExports ? [F(this.syntheticExports, e3, () => new Jn(this.astContext, e3, this.getSyntheticNamespace()))] : !s2 && this.options.shimMissingExports ? (this.shimMissingExport(e3), [this.exportShimVariable]) : [null];
  }
  hasEffects() {
    return "no-treeshake" === this.info.moduleSideEffects || this.ast.hasCachedEffects();
  }
  include() {
    const e3 = pt();
    this.ast.shouldBeIncluded(e3) && this.ast.include(e3, false);
  }
  includeAllExports(e3) {
    this.isExecuted || (mr(this), this.graph.needsTreeshakingPass = true);
    for (const t2 of this.exports.keys())
      if (e3 || t2 !== this.info.syntheticNamedExports) {
        const e4 = this.getVariableForExportName(t2)[0];
        e4.deoptimizePath(K), e4.included || this.includeVariable(e4);
      }
    for (const e4 of this.getReexports()) {
      const [t2] = this.getVariableForExportName(e4);
      t2 && (t2.deoptimizePath(K), t2.included || this.includeVariable(t2), t2 instanceof ue && (t2.module.reexported = true));
    }
    e3 && this.namespace.setMergedNamespaces(this.includeAndGetAdditionalMergedNamespaces());
  }
  includeAllInBundle() {
    this.ast.include(pt(), true), this.includeAllExports(false);
  }
  isIncluded() {
    return this.ast && (this.ast.included || this.namespace.included || this.importedFromNotTreeshaken);
  }
  linkImports() {
    this.addModulesToImportDescriptions(this.importDescriptions), this.addModulesToImportDescriptions(this.reexportDescriptions);
    const e3 = [];
    for (const t2 of this.exportAllSources) {
      const s2 = this.graph.modulesById.get(this.resolvedIds[t2].id);
      s2 instanceof ct ? e3.push(s2) : this.exportAllModules.push(s2);
    }
    this.exportAllModules.push(...e3);
  }
  render(e3) {
    const t2 = this.magicString.clone();
    this.ast.render(t2, e3), t2.trim();
    const { usesTopLevelAwait: s2 } = this.astContext;
    return s2 && "es" !== e3.format && "system" !== e3.format ? _e((i2 = this.id, n2 = e3.format, { code: "INVALID_TLA_FORMAT", id: i2, message: `Module format "${n2}" does not support top-level await. Use the "es" or "system" output formats rather.` })) : { source: t2, usesTopLevelAwait: s2 };
    var i2, n2;
  }
  setSource({ ast: e3, code: t2, customTransformCache: s2, originalCode: i2, originalSourcemap: n2, resolvedIds: r2, sourcemapChain: o2, transformDependencies: a2, transformFiles: l2, ...h2 }) {
    ur("generate ast", 3), this.info.code = t2, this.originalCode = i2, this.originalSourcemap = n2, this.sourcemapChain = o2, l2 && (this.transformFiles = l2), this.transformDependencies = a2, this.customTransformCache = s2, this.updateOptions(h2);
    const c2 = e3 ?? this.tryParse();
    dr("generate ast", 3), ur("analyze ast", 3), this.resolvedIds = r2 ?? /* @__PURE__ */ Object.create(null);
    const u2 = this.id;
    this.magicString = new m(t2, { filename: this.excludeFromSourcemap ? null : u2, indentExclusionRanges: [] }), this.astContext = { addDynamicImport: this.addDynamicImport.bind(this), addExport: this.addExport.bind(this), addImport: this.addImport.bind(this), addImportMeta: this.addImportMeta.bind(this), code: t2, deoptimizationTracker: this.graph.deoptimizationTracker, error: this.error.bind(this), fileName: u2, getExports: this.getExports.bind(this), getModuleExecIndex: () => this.execIndex, getModuleName: this.basename.bind(this), getNodeConstructor: (e4) => Kn[e4] || Kn.UnknownNode, getReexports: this.getReexports.bind(this), importDescriptions: this.importDescriptions, includeAllExports: () => this.includeAllExports(true), includeDynamicImport: this.includeDynamicImport.bind(this), includeVariableInModule: this.includeVariableInModule.bind(this), magicString: this.magicString, manualPureFunctions: this.graph.pureFunctions, module: this, moduleContext: this.context, options: this.options, requestTreeshakingPass: () => this.graph.needsTreeshakingPass = true, traceExport: (e4) => this.getVariableForExportName(e4)[0], traceVariable: this.traceVariable.bind(this), usesTopLevelAwait: false, warn: this.warn.bind(this) }, this.scope = new Gn(this.graph.scope, this.astContext), this.namespace = new Qn(this.astContext), this.ast = new Bn(c2, { context: this.astContext, type: "Module" }, this.scope), e3 || false !== this.options.cache ? this.info.ast = c2 : Object.defineProperty(this.info, "ast", { get: () => {
      if (this.graph.astLru.has(u2))
        return this.graph.astLru.get(u2);
      {
        const e4 = this.tryParse();
        return this.graph.astLru.set(u2, e4), e4;
      }
    } }), dr("analyze ast", 3);
  }
  toJSON() {
    return { assertions: this.info.assertions, ast: this.info.ast, code: this.info.code, customTransformCache: this.customTransformCache, dependencies: Array.from(this.dependencies, er), id: this.id, meta: this.info.meta, moduleSideEffects: this.info.moduleSideEffects, originalCode: this.originalCode, originalSourcemap: this.originalSourcemap, resolvedIds: this.resolvedIds, sourcemapChain: this.sourcemapChain, syntheticNamedExports: this.info.syntheticNamedExports, transformDependencies: this.transformDependencies, transformFiles: this.transformFiles };
  }
  traceVariable(e3, { importerForSideEffects: t2, isExportAllSearch: s2, searchedNamesAndModules: i2 } = pe) {
    const n2 = this.scope.variables.get(e3);
    if (n2)
      return n2;
    const r2 = this.importDescriptions.get(e3);
    if (r2) {
      const e4 = r2.module;
      if (e4 instanceof xr && "*" === r2.name)
        return e4.namespace;
      const [n3] = yr(e4, r2.name, t2 || this, s2, i2);
      return n3 || this.error(Qe(r2.name, this.id, e4.id), r2.start);
    }
    return null;
  }
  updateOptions({ meta: e3, moduleSideEffects: t2, syntheticNamedExports: s2 }) {
    null != t2 && (this.info.moduleSideEffects = t2), null != s2 && (this.info.syntheticNamedExports = s2), null != e3 && Object.assign(this.info.meta, e3);
  }
  warn(e3, t2) {
    this.addLocationToLogProps(e3, t2), this.options.onwarn(e3);
  }
  addDynamicImport(e3) {
    let t2 = e3.source;
    t2 instanceof zn ? 1 === t2.quasis.length && t2.quasis[0].value.cooked && (t2 = t2.quasis[0].value.cooked) : t2 instanceof wi && "string" == typeof t2.value && (t2 = t2.value), this.dynamicImports.push({ argument: t2, id: null, node: e3, resolution: null });
  }
  addExport(e3) {
    if (e3 instanceof ji)
      this.exports.set("default", { identifier: e3.variable.getAssignedVariableName(), localName: "default" });
    else if (e3 instanceof Fi) {
      const t2 = e3.source.value;
      if (this.addSource(t2, e3), e3.exported) {
        const s2 = e3.exported.name;
        this.reexportDescriptions.set(s2, { localName: "*", module: null, source: t2, start: e3.start });
      } else
        this.exportAllSources.add(t2);
    } else if (e3.source instanceof wi) {
      const t2 = e3.source.value;
      this.addSource(t2, e3);
      for (const { exported: s2, local: i2, start: n2 } of e3.specifiers) {
        const e4 = s2 instanceof wi ? s2.value : s2.name;
        this.reexportDescriptions.set(e4, { localName: i2 instanceof wi ? i2.value : i2.name, module: null, source: t2, start: n2 });
      }
    } else if (e3.declaration) {
      const t2 = e3.declaration;
      if (t2 instanceof Hn)
        for (const e4 of t2.declarations)
          for (const t3 of dt(e4.id))
            this.exports.set(t3, { identifier: null, localName: t3 });
      else {
        const e4 = t2.id.name;
        this.exports.set(e4, { identifier: null, localName: e4 });
      }
    } else
      for (const { local: t2, exported: s2 } of e3.specifiers) {
        const e4 = t2.name, i2 = s2 instanceof Qs ? s2.name : s2.value;
        this.exports.set(i2, { identifier: null, localName: e4 });
      }
  }
  addImport(e3) {
    const t2 = e3.source.value;
    this.addSource(t2, e3);
    for (const s2 of e3.specifiers) {
      const e4 = s2 instanceof Ki ? "default" : s2 instanceof wn ? "*" : s2.imported instanceof Qs ? s2.imported.name : s2.imported.value;
      this.importDescriptions.set(s2.local.name, { module: null, name: e4, source: t2, start: s2.start });
    }
  }
  addImportMeta(e3) {
    this.importMetas.push(e3);
  }
  addLocationToLogProps(e3, t2) {
    e3.id = this.id, e3.pos = t2;
    let s2 = this.info.code;
    const i2 = me(s2, t2, { offsetLine: 1 });
    if (i2) {
      let { column: n2, line: r2 } = i2;
      try {
        ({ column: n2, line: r2 } = Vn(this.sourcemapChain, { column: n2, line: r2 })), s2 = this.originalCode;
      } catch (e4) {
        this.options.onwarn(function(e5, t3, s3, i3, n3) {
          return { cause: e5, code: "SOURCEMAP_ERROR", id: t3, loc: { column: s3, file: t3, line: i3 }, message: `Error when using sourcemap for reporting an error: ${e5.message}`, pos: n3 };
        }(e4, this.id, n2, r2, t2));
      }
      Te(e3, { column: n2, line: r2 }, s2, this.id);
    }
  }
  addModulesToImportDescriptions(e3) {
    for (const t2 of e3.values()) {
      const { id: e4 } = this.resolvedIds[t2.source];
      t2.module = this.graph.modulesById.get(e4);
    }
  }
  addRelevantSideEffectDependencies(e3, t2, s2) {
    const i2 = /* @__PURE__ */ new Set(), n2 = (r2) => {
      for (const o2 of r2)
        i2.has(o2) || (i2.add(o2), t2.has(o2) ? e3.add(o2) : (o2.info.moduleSideEffects || s2.has(o2)) && (o2 instanceof ct || o2.hasEffects() ? e3.add(o2) : n2(o2.dependencies)));
    };
    n2(this.dependencies), n2(s2);
  }
  addSource(e3, t2) {
    var _a2;
    const s2 = ((_a2 = i2 = t2.assertions) == null ? void 0 : _a2.length) ? Object.fromEntries(i2.map((e4) => [tr(e4), e4.value.value])) : pe;
    var i2;
    const n2 = this.sourcesWithAssertions.get(e3);
    n2 ? sr(n2, s2) && this.warn(He(n2, s2, e3, this.id), t2.start) : this.sourcesWithAssertions.set(e3, s2);
  }
  getVariableFromNamespaceReexports(e3, t2, s2) {
    let i2 = null;
    const n2 = /* @__PURE__ */ new Map(), r2 = /* @__PURE__ */ new Set();
    for (const o3 of this.exportAllModules) {
      if (o3.info.syntheticNamedExports === e3)
        continue;
      const [a3, l3] = yr(o3, e3, t2, true, Er(s2));
      o3 instanceof ct || l3 ? r2.add(a3) : a3 instanceof Jn ? i2 || (i2 = a3) : a3 && n2.set(a3, o3);
    }
    if (n2.size > 0) {
      const t3 = [...n2], s3 = t3[0][0];
      return 1 === t3.length ? [s3] : (this.options.onwarn((o2 = e3, a2 = this.id, l2 = t3.map(([, e4]) => e4.id), { binding: o2, code: "NAMESPACE_CONFLICT", ids: l2, message: `Conflicting namespaces: "${O(a2)}" re-exports "${o2}" from one of the modules ${be(l2.map((e4) => O(e4)))} (will be ignored).`, reexporter: a2 })), [null]);
    }
    var o2, a2, l2;
    if (r2.size > 0) {
      const t3 = [...r2], s3 = t3[0];
      return t3.length > 1 && this.options.onwarn(function(e4, t4, s4, i3) {
        return { binding: e4, code: "AMBIGUOUS_EXTERNAL_NAMESPACES", ids: i3, message: `Ambiguous external namespace resolution: "${O(t4)}" re-exports "${e4}" from one of the external modules ${be(i3.map((e5) => O(e5)))}, guessing "${O(s4)}".`, reexporter: t4 };
      }(e3, this.id, s3.module.id, t3.map((e4) => e4.module.id))), [s3, true];
    }
    return i2 ? [i2] : [null];
  }
  includeAndGetAdditionalMergedNamespaces() {
    const e3 = /* @__PURE__ */ new Set(), t2 = /* @__PURE__ */ new Set();
    for (const s2 of [this, ...this.exportAllModules])
      if (s2 instanceof ct) {
        const [t3] = s2.getVariableForExportName("*");
        t3.include(), this.includedImports.add(t3), e3.add(t3);
      } else if (s2.info.syntheticNamedExports) {
        const e4 = s2.getSyntheticNamespace();
        e4.include(), this.includedImports.add(e4), t2.add(e4);
      }
    return [...t2, ...e3];
  }
  includeDynamicImport(e3) {
    const t2 = this.dynamicImports.find((t3) => t3.node === e3).resolution;
    t2 instanceof xr && (t2.includedDynamicImporters.push(this), t2.includeAllExports(true));
  }
  includeVariable(e3) {
    if (!e3.included) {
      e3.include(), this.graph.needsTreeshakingPass = true;
      const t2 = e3.module;
      if (t2 instanceof xr && (t2.isExecuted || mr(t2), t2 !== this)) {
        const t3 = function(e4, t4) {
          const s2 = F(t4.sideEffectDependenciesByVariable, e4, z);
          let i2 = e4;
          const n2 = /* @__PURE__ */ new Set([i2]);
          for (; ; ) {
            const e5 = i2.module;
            if (i2 = i2 instanceof Un ? i2.getDirectOriginalVariable() : i2 instanceof Jn ? i2.syntheticNamespace : null, !i2 || n2.has(i2))
              break;
            n2.add(i2), s2.add(e5);
            const t5 = e5.sideEffectDependenciesByVariable.get(i2);
            if (t5)
              for (const e6 of t5)
                s2.add(e6);
          }
          return s2;
        }(e3, this);
        for (const e4 of t3)
          e4.isExecuted || mr(e4);
      }
    }
  }
  includeVariableInModule(e3) {
    this.includeVariable(e3);
    const t2 = e3.module;
    t2 && t2 !== this && this.includedImports.add(e3);
  }
  shimMissingExport(e3) {
    var t2, s2;
    this.options.onwarn((t2 = this.id, { binding: s2 = e3, code: "SHIMMED_EXPORT", exporter: t2, message: `Missing export "${s2}" has been shimmed in module "${O(t2)}".` })), this.exports.set(e3, gr);
  }
  tryParse() {
    try {
      return this.graph.contextParse(this.info.code);
    } catch (e3) {
      return this.error(function(e4, t2) {
        let s2 = e4.message.replace(/ \(\d+:\d+\)$/, "");
        return t2.endsWith(".json") ? s2 += " (Note that you need @rollup/plugin-json to import JSON files)" : t2.endsWith(".js") || (s2 += " (Note that you need plugins to import files that are not JavaScript)"), { cause: e4, code: "PARSE_ERROR", id: t2, message: s2 };
      }(e3, this.id), e3.pos);
    }
  }
}
function br(e3, t2, s2) {
  if (e3.module instanceof xr && e3.module !== s2) {
    const i2 = e3.module.cycles;
    if (i2.size > 0) {
      const n2 = s2.cycles;
      for (const r2 of n2)
        if (i2.has(r2)) {
          t2.alternativeReexportModules.set(e3, s2);
          break;
        }
    }
  }
}
const Er = (e3) => e3 && new Map(Array.from(e3, ([e4, t2]) => [e4, new Set(t2)]));
function vr(e3) {
  return e3.endsWith(".js") ? e3.slice(0, -3) : e3;
}
function Sr(e3, t2) {
  return e3.autoId ? `${e3.basePath ? e3.basePath + "/" : ""}${vr(t2)}` : e3.id ?? "";
}
function kr(e3, t2, s2, i2, n2, r2, o2, a2 = "return ") {
  const { _: l2, getDirectReturnFunction: h2, getFunctionIntro: c2, getPropertyAccess: u2, n: d2, s: p2 } = n2;
  if (!s2)
    return `${d2}${d2}${a2}${function(e4, t3, s3, i3, n3) {
      if (e4.length > 0)
        return e4[0].local;
      for (const { defaultVariableName: e5, importPath: r3, isChunk: o3, name: a3, namedExportsMode: l3, namespaceVariableName: h3, reexports: c3 } of t3)
        if (c3)
          return Ar(a3, c3[0].imported, l3, o3, e5, h3, s3, r3, i3, n3);
    }(e3, t2, i2, o2, u2)};`;
  let f2 = "";
  for (const { defaultVariableName: e4, importPath: n3, isChunk: a3, name: c3, namedExportsMode: p3, namespaceVariableName: m2, reexports: g2 } of t2)
    if (g2 && s2) {
      for (const t3 of g2)
        if ("*" !== t3.reexported) {
          const s3 = Ar(c3, t3.imported, p3, a3, e4, m2, i2, n3, o2, u2);
          if (f2 && (f2 += d2), "*" !== t3.imported && t3.needsLiveBinding) {
            const [e5, i3] = h2([], { functionReturn: true, lineBreakIndent: null, name: null });
            f2 += `Object.defineProperty(exports,${l2}'${t3.reexported}',${l2}{${d2}${r2}enumerable:${l2}true,${d2}${r2}get:${l2}${e5}${s3}${i3}${d2}});`;
          } else
            f2 += `exports${u2(t3.reexported)}${l2}=${l2}${s3};`;
        }
    }
  for (const { exported: t3, local: s3 } of e3) {
    const e4 = `exports${u2(t3)}`;
    e4 !== s3 && (f2 && (f2 += d2), f2 += `${e4}${l2}=${l2}${s3};`);
  }
  for (const { name: e4, reexports: i3 } of t2)
    if (i3 && s2) {
      for (const t3 of i3)
        if ("*" === t3.reexported) {
          f2 && (f2 += d2);
          const s3 = `{${d2}${r2}if${l2}(k${l2}!==${l2}'default'${l2}&&${l2}!exports.hasOwnProperty(k))${l2}${Pr(e4, t3.needsLiveBinding, r2, n2)}${p2}${d2}}`;
          f2 += `Object.keys(${e4}).forEach(${c2(["k"], { isAsync: false, name: null })}${s3});`;
        }
    }
  return f2 ? `${d2}${d2}${f2}` : "";
}
function Ar(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2) {
  if ("default" === t2) {
    if (!i2) {
      const t3 = o2(a2), s3 = sn[t3] ? n2 : e3;
      return nn(t3, l2) ? `${s3}${h2("default")}` : s3;
    }
    return s2 ? `${e3}${h2("default")}` : e3;
  }
  return "*" === t2 ? (i2 ? !s2 : rn[o2(a2)]) ? r2 : e3 : `${e3}${h2(t2)}`;
}
function wr(e3) {
  return e3([["value", "true"]], { lineBreakIndent: null });
}
function Ir(e3, t2, s2, { _: i2, getObject: n2 }) {
  if (e3) {
    if (t2)
      return s2 ? `Object.defineProperties(exports,${i2}${n2([["__esModule", wr(n2)], [null, `[Symbol.toStringTag]:${i2}${Sn(n2)}`]], { lineBreakIndent: null })});` : `Object.defineProperty(exports,${i2}'__esModule',${i2}${wr(n2)});`;
    if (s2)
      return `Object.defineProperty(exports,${i2}Symbol.toStringTag,${i2}${Sn(n2)});`;
  }
  return "";
}
const Pr = (e3, t2, s2, { _: i2, getDirectReturnFunction: n2, n: r2 }) => {
  if (t2) {
    const [t3, o2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
    return `Object.defineProperty(exports,${i2}k,${i2}{${r2}${s2}${s2}enumerable:${i2}true,${r2}${s2}${s2}get:${i2}${t3}${e3}[k]${o2}${r2}${s2}})`;
  }
  return `exports[k]${i2}=${i2}${e3}[k]`;
};
function Cr(e3, t2, s2, i2, n2, r2, o2, a2) {
  const { _: l2, cnst: h2, n: c2 } = a2, u2 = /* @__PURE__ */ new Set(), d2 = [], p2 = (e4, t3, s3) => {
    u2.add(t3), d2.push(`${h2} ${e4}${l2}=${l2}/*#__PURE__*/${t3}(${s3});`);
  };
  for (const { defaultVariableName: s3, imports: i3, importPath: n3, isChunk: r3, name: o3, namedExportsMode: a3, namespaceVariableName: l3, reexports: h3 } of e3)
    if (r3) {
      for (const { imported: e4, reexported: t3 } of [...i3 || [], ...h3 || []])
        if ("*" === e4 && "*" !== t3) {
          a3 || p2(l3, en, o3);
          break;
        }
    } else {
      const e4 = t2(n3);
      let r4 = false, a4 = false;
      for (const { imported: t3, reexported: n4 } of [...i3 || [], ...h3 || []]) {
        let i4, h4;
        "default" === t3 ? r4 || (r4 = true, s3 !== l3 && (h4 = s3, i4 = sn[e4])) : "*" !== t3 || "*" === n4 || a4 || (a4 = true, i4 = rn[e4], h4 = l3), i4 && p2(h4, i4, o3);
      }
    }
  return `${an(u2, r2, o2, a2, s2, i2, n2)}${d2.length > 0 ? `${d2.join(c2)}${c2}${c2}` : ""}`;
}
function $r(e3, t2) {
  return "." !== e3[0] ? e3 : t2 ? (s2 = e3).endsWith(".js") ? s2 : s2 + ".js" : vr(e3);
  var s2;
}
var Nr = {}, _r = ["assert", "async_hooks", "buffer", "child_process", "cluster", "console", "constants", "crypto", "dgram", "diagnostics_channel", "dns", "domain", "events", "fs", "http", "http2", "https", "inspector", "module", "net", "os", "path", "perf_hooks", "process", "punycode", "querystring", "readline", "repl", "stream", "string_decoder", "timers", "tls", "trace_events", "tty", "url", "util", "v8", "vm", "wasi", "worker_threads", "zlib"];
({ get exports() {
  return Nr;
}, set exports(e3) {
  Nr = e3;
} }).exports = _r;
const Tr = /* @__PURE__ */ new Set([...t(Nr), "assert/strict", "dns/promises", "fs/promises", "path/posix", "path/win32", "readline/promises", "stream/consumers", "stream/promises", "stream/web", "timers/promises", "util/types"]);
function Rr(e3, t2) {
  const s2 = t2.map(({ importPath: e4 }) => e4).filter((e4) => Tr.has(e4) || e4.startsWith("node:"));
  0 !== s2.length && e3(function(e4) {
    return { code: "MISSING_NODE_BUILTINS", ids: e4, message: `Creating a browser bundle that depends on Node.js built-in modules (${be(e4)}). You might need to include https://github.com/FredKSchott/rollup-plugin-polyfill-node` };
  }(s2));
}
const Mr = (e3, t2) => e3.split(".").map(t2).join("");
function Or(e3, t2, s2, i2, { _: n2, getPropertyAccess: r2 }) {
  const o2 = e3.split(".");
  o2[0] = ("function" == typeof s2 ? s2(o2[0]) : s2[o2[0]]) || o2[0];
  const a2 = o2.pop();
  let l2 = t2, h2 = [...o2.map((e4) => (l2 += r2(e4), `${l2}${n2}=${n2}${l2}${n2}||${n2}{}`)), `${l2}${r2(a2)}`].join(`,${n2}`) + `${n2}=${n2}${i2}`;
  return o2.length > 0 && (h2 = `(${h2})`), h2;
}
function Dr(e3) {
  let t2 = e3.length;
  for (; t2--; ) {
    const { imports: s2, reexports: i2 } = e3[t2];
    if (s2 || i2)
      return e3.slice(0, t2 + 1);
  }
  return [];
}
const Lr = ({ dependencies: e3, exports: t2 }) => {
  const s2 = new Set(t2.map((e4) => e4.exported));
  s2.add("default");
  for (const { reexports: t3 } of e3)
    if (t3)
      for (const e4 of t3)
        "*" !== e4.reexported && s2.add(e4.reexported);
  return s2;
}, Vr = (e3, t2, { _: s2, cnst: i2, getObject: n2, n: r2 }) => e3 ? `${r2}${t2}${i2} _starExcludes${s2}=${s2}${n2([...e3].map((e4) => [e4, "1"]), { lineBreakIndent: { base: t2, t: t2 } })};` : "", Br = (e3, t2, { _: s2, n: i2 }) => e3.length > 0 ? `${i2}${t2}var ${e3.join(`,${s2}`)};` : "", Fr = (e3, t2, s2) => zr(e3.filter((e4) => e4.hoisted).map((e4) => ({ name: e4.exported, value: e4.local })), t2, s2);
function zr(e3, t2, { _: s2, n: i2 }) {
  return 0 === e3.length ? "" : 1 === e3.length ? `exports('${e3[0].name}',${s2}${e3[0].value});${i2}${i2}` : `exports({${i2}` + e3.map(({ name: e4, value: i3 }) => `${t2}${e4}:${s2}${i3}`).join(`,${i2}`) + `${i2}});${i2}${i2}`;
}
const jr = (e3, t2, s2) => zr(e3.filter((e4) => e4.expression).map((e4) => ({ name: e4.exported, value: e4.local })), t2, s2), Ur = (e3, t2, s2) => zr(e3.filter((e4) => e4.local === Yn).map((e4) => ({ name: e4.exported, value: Yn })), t2, s2);
function Gr(e3, t2, s2) {
  return e3 ? `${t2}${Mr(e3, s2)}` : "null";
}
var Wr = { amd: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, id: o2, indent: a2, intro: l2, isEntryFacade: h2, isModuleFacade: c2, namedExportsMode: u2, outro: d2, snippets: p2, onwarn: f2 }, { amd: m2, esModule: g2, externalLiveBindings: y2, freeze: x2, interop: b2, namespaceToStringTag: E2, strict: v2 }) {
  Rr(f2, s2);
  const S2 = s2.map((e4) => `'${$r(e4.importPath, m2.forceJsExtensionForImports)}'`), k2 = s2.map((e4) => e4.name), { n: A2, getNonArrowFunctionIntro: w2, _: I2 } = p2;
  u2 && r2 && (k2.unshift("exports"), S2.unshift("'exports'")), t2.has("require") && (k2.unshift("require"), S2.unshift("'require'")), t2.has("module") && (k2.unshift("module"), S2.unshift("'module'"));
  const P2 = Sr(m2, o2), C2 = (P2 ? `'${P2}',${I2}` : "") + (S2.length > 0 ? `[${S2.join(`,${I2}`)}],${I2}` : ""), $2 = v2 ? `${I2}'use strict';` : "";
  e3.prepend(`${l2}${Cr(s2, b2, y2, x2, E2, t2, a2, p2)}`);
  const N2 = kr(i2, s2, u2, b2, p2, a2, y2);
  let _2 = Ir(u2 && r2, h2 && (true === g2 || "if-default-prop" === g2 && n2), c2 && E2, p2);
  _2 && (_2 = A2 + A2 + _2), e3.append(`${N2}${_2}${d2}`).indent(a2).prepend(`${m2.define}(${C2}(${w2(k2, { isAsync: false, name: null })}{${$2}${A2}${A2}`).append(`${A2}${A2}}));`);
}, cjs: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, indent: o2, intro: a2, isEntryFacade: l2, isModuleFacade: h2, namedExportsMode: c2, outro: u2, snippets: d2 }, { compact: p2, esModule: f2, externalLiveBindings: m2, freeze: g2, interop: y2, namespaceToStringTag: x2, strict: b2 }) {
  const { _: E2, n: v2 } = d2, S2 = b2 ? `'use strict';${v2}${v2}` : "";
  let k2 = Ir(c2 && r2, l2 && (true === f2 || "if-default-prop" === f2 && n2), h2 && x2, d2);
  k2 && (k2 += v2 + v2);
  const A2 = function(e4, { _: t3, cnst: s3, n: i3 }, n3) {
    let r3 = "", o3 = false;
    for (const { importPath: a3, name: l3, reexports: h3, imports: c3 } of e4)
      h3 || c3 ? (r3 += n3 && o3 ? "," : `${r3 ? `;${i3}` : ""}${s3} `, o3 = true, r3 += `${l3}${t3}=${t3}require('${a3}')`) : (r3 && (r3 += n3 && !o3 ? "," : `;${i3}`), o3 = false, r3 += `require('${a3}')`);
    if (r3)
      return `${r3};${i3}${i3}`;
    return "";
  }(s2, d2, p2), w2 = Cr(s2, y2, m2, g2, x2, t2, o2, d2);
  e3.prepend(`${S2}${a2}${k2}${A2}${w2}`);
  const I2 = kr(i2, s2, c2, y2, d2, o2, m2, `module.exports${E2}=${E2}`);
  e3.append(`${I2}${u2}`);
}, es: function(e3, { accessedGlobals: t2, indent: s2, intro: i2, outro: n2, dependencies: r2, exports: o2, snippets: a2 }, { externalLiveBindings: l2, freeze: h2, namespaceToStringTag: c2 }) {
  const { n: u2 } = a2, d2 = function(e4, { _: t3 }) {
    const s3 = [];
    for (const { importPath: i3, reexports: n3, imports: r3, name: o3, assertions: a3 } of e4) {
      const e5 = `'${i3}'${a3 ? `${t3}assert${t3}${a3}` : ""};`;
      if (n3 || r3) {
        if (r3) {
          let i4 = null, n4 = null;
          const o4 = [];
          for (const e6 of r3)
            "default" === e6.imported ? i4 = e6 : "*" === e6.imported ? n4 = e6 : o4.push(e6);
          n4 && s3.push(`import${t3}*${t3}as ${n4.local} from${t3}${e5}`), i4 && 0 === o4.length ? s3.push(`import ${i4.local} from${t3}${e5}`) : o4.length > 0 && s3.push(`import ${i4 ? `${i4.local},${t3}` : ""}{${t3}${o4.map((e6) => e6.imported === e6.local ? e6.imported : `${e6.imported} as ${e6.local}`).join(`,${t3}`)}${t3}}${t3}from${t3}${e5}`);
        }
        if (n3) {
          let i4 = null;
          const a4 = [], l3 = [];
          for (const e6 of n3)
            "*" === e6.reexported ? i4 = e6 : "*" === e6.imported ? a4.push(e6) : l3.push(e6);
          if (i4 && s3.push(`export${t3}*${t3}from${t3}${e5}`), a4.length > 0) {
            r3 && r3.some((e6) => "*" === e6.imported && e6.local === o3) || s3.push(`import${t3}*${t3}as ${o3} from${t3}${e5}`);
            for (const e6 of a4)
              s3.push(`export${t3}{${t3}${o3 === e6.reexported ? o3 : `${o3} as ${e6.reexported}`} };`);
          }
          l3.length > 0 && s3.push(`export${t3}{${t3}${l3.map((e6) => e6.imported === e6.reexported ? e6.imported : `${e6.imported} as ${e6.reexported}`).join(`,${t3}`)}${t3}}${t3}from${t3}${e5}`);
        }
      } else
        s3.push(`import${t3}${e5}`);
    }
    return s3;
  }(r2, a2);
  d2.length > 0 && (i2 += d2.join(u2) + u2 + u2), (i2 += an(null, t2, s2, a2, l2, h2, c2)) && e3.prepend(i2);
  const p2 = function(e4, { _: t3, cnst: s3 }) {
    const i3 = [], n3 = [];
    for (const r3 of e4)
      r3.expression && i3.push(`${s3} ${r3.local}${t3}=${t3}${r3.expression};`), n3.push(r3.exported === r3.local ? r3.local : `${r3.local} as ${r3.exported}`);
    n3.length > 0 && i3.push(`export${t3}{${t3}${n3.join(`,${t3}`)}${t3}};`);
    return i3;
  }(o2, a2);
  p2.length > 0 && e3.append(u2 + u2 + p2.join(u2).trim()), n2 && e3.append(n2), e3.trim();
}, iife: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, indent: o2, intro: a2, namedExportsMode: l2, outro: h2, snippets: c2, onwarn: u2 }, { compact: d2, esModule: p2, extend: f2, freeze: m2, externalLiveBindings: g2, globals: y2, interop: x2, name: b2, namespaceToStringTag: E2, strict: v2 }) {
  const { _: S2, getNonArrowFunctionIntro: k2, getPropertyAccess: A2, n: w2 } = c2, I2 = b2 && b2.includes("."), P2 = !f2 && !I2;
  if (b2 && P2 && (lt(C2 = b2) || at.test(C2)))
    return _e(function(e4) {
      return { code: "ILLEGAL_IDENTIFIER_AS_NAME", message: `Given name "${e4}" is not a legal JS identifier. If you need this, you can try "output.extend: true".`, url: Ee("configuration-options/#output-extend") };
    }(b2));
  var C2;
  Rr(u2, s2);
  const $2 = Dr(s2), N2 = $2.map((e4) => e4.globalName || "null"), _2 = $2.map((e4) => e4.name);
  r2 && !b2 && u2({ code: Le, message: 'If you do not supply "output.name", you may not be able to access the exports of an IIFE bundle.', url: Ee(Ce) }), l2 && r2 && (f2 ? (N2.unshift(`this${Mr(b2, A2)}${S2}=${S2}this${Mr(b2, A2)}${S2}||${S2}{}`), _2.unshift("exports")) : (N2.unshift("{}"), _2.unshift("exports")));
  const T2 = v2 ? `${o2}'use strict';${w2}` : "", R2 = Cr(s2, x2, g2, m2, E2, t2, o2, c2);
  e3.prepend(`${a2}${R2}`);
  let M2 = `(${k2(_2, { isAsync: false, name: null })}{${w2}${T2}${w2}`;
  r2 && (!b2 || f2 && l2 || (M2 = (P2 ? `var ${b2}` : `this${Mr(b2, A2)}`) + `${S2}=${S2}${M2}`), I2 && (M2 = function(e4, t3, s3, { _: i3, getPropertyAccess: n3, s: r3 }, o3) {
    const a3 = e4.split(".");
    a3[0] = ("function" == typeof s3 ? s3(a3[0]) : s3[a3[0]]) || a3[0], a3.pop();
    let l3 = t3;
    return a3.map((e5) => (l3 += n3(e5), `${l3}${i3}=${i3}${l3}${i3}||${i3}{}${r3}`)).join(o3 ? "," : "\n") + (o3 && a3.length > 0 ? ";" : "\n");
  }(b2, "this", y2, c2, d2) + M2));
  let O2 = `${w2}${w2}})(${N2.join(`,${S2}`)});`;
  r2 && !f2 && l2 && (O2 = `${w2}${w2}${o2}return exports;${O2}`);
  const D2 = kr(i2, s2, l2, x2, c2, o2, g2);
  let L2 = Ir(l2 && r2, true === p2 || "if-default-prop" === p2 && n2, E2, c2);
  L2 && (L2 = w2 + w2 + L2), e3.append(`${D2}${L2}${h2}`).indent(o2).prepend(M2).append(O2);
}, system: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasExports: n2, indent: r2, intro: o2, snippets: a2, outro: l2, usesTopLevelAwait: h2 }, { externalLiveBindings: c2, freeze: u2, name: d2, namespaceToStringTag: p2, strict: f2, systemNullSetters: m2 }) {
  const { _: g2, getFunctionIntro: y2, getNonArrowFunctionIntro: x2, n: b2, s: E2 } = a2, { importBindings: v2, setters: S2, starExcludes: k2 } = function(e4, t3, s3, { _: i3, cnst: n3, getObject: r3, getPropertyAccess: o3, n: a3 }) {
    const l3 = [], h3 = [];
    let c3 = null;
    for (const { imports: u3, reexports: d3 } of e4) {
      const p3 = [];
      if (u3)
        for (const e5 of u3)
          l3.push(e5.local), "*" === e5.imported ? p3.push(`${e5.local}${i3}=${i3}module;`) : p3.push(`${e5.local}${i3}=${i3}module${o3(e5.imported)};`);
      if (d3) {
        const a4 = [];
        let l4 = false;
        for (const { imported: e5, reexported: t4 } of d3)
          "*" === t4 ? l4 = true : a4.push([t4, "*" === e5 ? "module" : `module${o3(e5)}`]);
        if (a4.length > 1 || l4) {
          const o4 = r3(a4, { lineBreakIndent: null });
          l4 ? (c3 || (c3 = Lr({ dependencies: e4, exports: t3 })), p3.push(`${n3} setter${i3}=${i3}${o4};`, `for${i3}(${n3} name in module)${i3}{`, `${s3}if${i3}(!_starExcludes[name])${i3}setter[name]${i3}=${i3}module[name];`, "}", "exports(setter);")) : p3.push(`exports(${o4});`);
        } else {
          const [e5, t4] = a4[0];
          p3.push(`exports('${e5}',${i3}${t4});`);
        }
      }
      h3.push(p3.join(`${a3}${s3}${s3}${s3}`));
    }
    return { importBindings: l3, setters: h3, starExcludes: c3 };
  }(s2, i2, r2, a2), A2 = d2 ? `'${d2}',${g2}` : "", w2 = t2.has("module") ? ["exports", "module"] : n2 ? ["exports"] : [];
  let I2 = `System.register(${A2}[` + s2.map(({ importPath: e4 }) => `'${e4}'`).join(`,${g2}`) + `],${g2}(${x2(w2, { isAsync: false, name: null })}{${b2}${r2}${f2 ? "'use strict';" : ""}` + Vr(k2, r2, a2) + Br(v2, r2, a2) + `${b2}${r2}return${g2}{${S2.length > 0 ? `${b2}${r2}${r2}setters:${g2}[${S2.map((e4) => e4 ? `${y2(["module"], { isAsync: false, name: null })}{${b2}${r2}${r2}${r2}${e4}${b2}${r2}${r2}}` : m2 ? "null" : `${y2([], { isAsync: false, name: null })}{}`).join(`,${g2}`)}],` : ""}${b2}`;
  I2 += `${r2}${r2}execute:${g2}(${x2([], { isAsync: h2, name: null })}{${b2}${b2}`;
  const P2 = `${r2}${r2}})${b2}${r2}}${E2}${b2}}));`;
  e3.prepend(o2 + an(null, t2, r2, a2, c2, u2, p2) + Fr(i2, r2, a2)).append(`${l2}${b2}${b2}` + jr(i2, r2, a2) + Ur(i2, r2, a2)).indent(`${r2}${r2}${r2}`).append(P2).prepend(I2);
}, umd: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, id: o2, indent: a2, intro: l2, namedExportsMode: h2, outro: c2, snippets: u2, onwarn: d2 }, { amd: p2, compact: f2, esModule: m2, extend: g2, externalLiveBindings: y2, freeze: x2, interop: b2, name: E2, namespaceToStringTag: v2, globals: S2, noConflict: k2, strict: A2 }) {
  const { _: w2, cnst: I2, getFunctionIntro: P2, getNonArrowFunctionIntro: C2, getPropertyAccess: $2, n: N2, s: _2 } = u2, T2 = f2 ? "f" : "factory", R2 = f2 ? "g" : "global";
  if (r2 && !E2)
    return _e({ code: Le, message: 'You must supply "output.name" for UMD bundles that have exports so that the exports are accessible in environments without a module loader.', url: Ee(Ce) });
  Rr(d2, s2);
  const M2 = s2.map((e4) => `'${$r(e4.importPath, p2.forceJsExtensionForImports)}'`), O2 = s2.map((e4) => `require('${e4.importPath}')`), D2 = Dr(s2), L2 = D2.map((e4) => Gr(e4.globalName, R2, $2)), V2 = D2.map((e4) => e4.name);
  h2 && (r2 || k2) && (M2.unshift("'exports'"), O2.unshift("exports"), L2.unshift(Or(E2, R2, S2, (g2 ? `${Gr(E2, R2, $2)}${w2}||${w2}` : "") + "{}", u2)), V2.unshift("exports"));
  const B2 = Sr(p2, o2), F2 = (B2 ? `'${B2}',${w2}` : "") + (M2.length > 0 ? `[${M2.join(`,${w2}`)}],${w2}` : ""), z2 = p2.define, j2 = !h2 && r2 ? `module.exports${w2}=${w2}` : "", U2 = A2 ? `${w2}'use strict';${N2}` : "";
  let G2;
  if (k2) {
    const e4 = f2 ? "e" : "exports";
    let t3;
    if (!h2 && r2)
      t3 = `${I2} ${e4}${w2}=${w2}${Or(E2, R2, S2, `${T2}(${L2.join(`,${w2}`)})`, u2)};`;
    else {
      t3 = `${I2} ${e4}${w2}=${w2}${L2.shift()};${N2}${a2}${a2}${T2}(${[e4, ...L2].join(`,${w2}`)});`;
    }
    G2 = `(${P2([], { isAsync: false, name: null })}{${N2}${a2}${a2}${I2} current${w2}=${w2}${function(e5, t4, { _: s3, getPropertyAccess: i3 }) {
      let n3 = t4;
      return e5.split(".").map((e6) => n3 += i3(e6)).join(`${s3}&&${s3}`);
    }(E2, R2, u2)};${N2}${a2}${a2}${t3}${N2}${a2}${a2}${e4}.noConflict${w2}=${w2}${P2([], { isAsync: false, name: null })}{${w2}${Gr(E2, R2, $2)}${w2}=${w2}current;${w2}return ${e4}${_2}${w2}};${N2}${a2}})()`;
  } else
    G2 = `${T2}(${L2.join(`,${w2}`)})`, !h2 && r2 && (G2 = Or(E2, R2, S2, G2, u2));
  const W2 = r2 || k2 && h2 || L2.length > 0, q2 = [T2];
  W2 && q2.unshift(R2);
  const H2 = W2 ? `this,${w2}` : "", K2 = W2 ? `(${R2}${w2}=${w2}typeof globalThis${w2}!==${w2}'undefined'${w2}?${w2}globalThis${w2}:${w2}${R2}${w2}||${w2}self,${w2}` : "", Y2 = W2 ? ")" : "", X2 = W2 ? `${a2}typeof exports${w2}===${w2}'object'${w2}&&${w2}typeof module${w2}!==${w2}'undefined'${w2}?${w2}${j2}${T2}(${O2.join(`,${w2}`)})${w2}:${N2}` : "", Q2 = `(${C2(q2, { isAsync: false, name: null })}{${N2}` + X2 + `${a2}typeof ${z2}${w2}===${w2}'function'${w2}&&${w2}${z2}.amd${w2}?${w2}${z2}(${F2}${T2})${w2}:${N2}${a2}${K2}${G2}${Y2};${N2}})(${H2}(${C2(V2, { isAsync: false, name: null })}{${U2}${N2}`, J2 = N2 + N2 + "}));";
  e3.prepend(`${l2}${Cr(s2, b2, y2, x2, v2, t2, a2, u2)}`);
  const Z2 = kr(i2, s2, h2, b2, u2, a2, y2);
  let ee2 = Ir(h2 && r2, true === m2 || "if-default-prop" === m2 && n2, v2, u2);
  ee2 && (ee2 = N2 + N2 + ee2), e3.append(`${Z2}${ee2}${c2}`).trim().indent(a2).append(J2).prepend(Q2);
} };
const qr = (e3, t2) => t2 ? `${e3}
${t2}` : e3, Hr = (e3, t2) => t2 ? `${e3}

${t2}` : e3;
const Kr = { amd: Qr, cjs: Qr, es: Xr, iife: Qr, system: Xr, umd: Qr };
function Yr(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2, c2, u2, d2, p2) {
  const f2 = [...e3].reverse();
  for (const e4 of f2)
    e4.scope.addUsedOutsideNames(i2, n2, u2, d2);
  !function(e4, t3, s3) {
    for (const i3 of t3) {
      for (const t4 of i3.scope.variables.values())
        t4.included && !(t4.renderBaseName || t4 instanceof Un && t4.getOriginalVariable() !== t4) && t4.setRenderNames(null, Cs(t4.name, e4, t4.forbiddenNames));
      if (s3.has(i3)) {
        const t4 = i3.namespace;
        t4.setRenderNames(null, Cs(t4.name, e4, t4.forbiddenNames));
      }
    }
  }(i2, f2, p2), Kr[n2](i2, s2, t2, r2, o2, a2, l2, h2, c2);
  for (const e4 of f2)
    e4.scope.deconflict(n2, u2, d2);
}
function Xr(e3, t2, s2, i2, n2, r2, o2, a2, l2) {
  for (const t3 of s2.dependencies)
    (n2 || t3 instanceof B) && (t3.variableName = Cs(t3.suggestedVariableName, e3, null));
  for (const s3 of t2) {
    const t3 = s3.module, i3 = s3.name;
    s3.isNamespace && (n2 || t3 instanceof ct) ? s3.setRenderNames(null, (t3 instanceof ct ? a2.get(t3) : o2.get(t3)).variableName) : t3 instanceof ct && "default" === i3 ? s3.setRenderNames(null, Cs([...t3.exportedVariables].some(([e4, t4]) => "*" === t4 && e4.included) ? t3.suggestedVariableName + "__default" : t3.suggestedVariableName, e3, s3.forbiddenNames)) : s3.setRenderNames(null, Cs(i3, e3, s3.forbiddenNames));
  }
  for (const t3 of l2)
    t3.setRenderNames(null, Cs(t3.name, e3, t3.forbiddenNames));
}
function Qr(e3, t2, { deconflictedDefault: s2, deconflictedNamespace: i2, dependencies: n2 }, r2, o2, a2, l2, h2) {
  for (const t3 of n2)
    t3.variableName = Cs(t3.suggestedVariableName, e3, null);
  for (const t3 of i2)
    t3.namespaceVariableName = Cs(`${t3.suggestedVariableName}__namespace`, e3, null);
  for (const t3 of s2)
    t3.defaultVariableName = i2.has(t3) && on(r2(t3.id), a2) ? t3.namespaceVariableName : Cs(`${t3.suggestedVariableName}__default`, e3, null);
  for (const e4 of t2) {
    const t3 = e4.module;
    if (t3 instanceof ct) {
      const s3 = h2.get(t3), i3 = e4.name;
      if ("default" === i3) {
        const i4 = r2(t3.id), n3 = sn[i4] ? s3.defaultVariableName : s3.variableName;
        nn(i4, a2) ? e4.setRenderNames(n3, "default") : e4.setRenderNames(null, n3);
      } else
        "*" === i3 ? e4.setRenderNames(null, rn[r2(t3.id)] ? s3.namespaceVariableName : s3.variableName) : e4.setRenderNames(s3.variableName, null);
    } else {
      const s3 = l2.get(t3);
      o2 && e4.isNamespace ? e4.setRenderNames(null, "default" === s3.exportMode ? s3.namespaceVariableName : s3.variableName) : "default" === s3.exportMode ? e4.setRenderNames(null, s3.variableName) : e4.setRenderNames(s3.variableName, s3.getVariableExportName(e4));
    }
  }
}
function Jr(e3, { exports: t2, name: s2, format: i2 }, n2, r2) {
  const o2 = e3.getExportNames();
  if ("default" === t2) {
    if (1 !== o2.length || "default" !== o2[0])
      return _e(Ye("default", o2, n2));
  } else if ("none" === t2 && o2.length > 0)
    return _e(Ye("none", o2, n2));
  return "auto" === t2 && (0 === o2.length ? t2 = "none" : 1 === o2.length && "default" === o2[0] ? t2 = "default" : ("es" !== i2 && "system" !== i2 && o2.includes("default") && r2(function(e4, t3) {
    return { code: "MIXED_EXPORTS", id: e4, message: `Entry module "${O(e4)}" is using named and default exports together. Consumers of your bundle will have to use \`${t3 || "chunk"}.default\` to access the default export, which may not be what you want. Use \`output.exports: "named"\` to disable this warning.`, url: Ee(ke) };
  }(n2, s2)), t2 = "named")), t2;
}
function Zr(e3) {
  const t2 = e3.split("\n"), s2 = t2.filter((e4) => /^\t+/.test(e4)), i2 = t2.filter((e4) => /^ {2,}/.test(e4));
  if (0 === s2.length && 0 === i2.length)
    return null;
  if (s2.length >= i2.length)
    return "	";
  const n2 = i2.reduce((e4, t3) => {
    const s3 = /^ +/.exec(t3)[0].length;
    return Math.min(s3, e4);
  }, 1 / 0);
  return " ".repeat(n2);
}
function eo(e3, t2, s2, i2, n2, r2) {
  const o2 = e3.getDependenciesToBeIncluded();
  for (const e4 of o2) {
    if (e4 instanceof ct) {
      t2.push(r2.get(e4));
      continue;
    }
    const o3 = n2.get(e4);
    o3 === i2 ? s2.has(e4) || (s2.add(e4), eo(e4, t2, s2, i2, n2, r2)) : t2.push(o3);
  }
}
const to = "!~{", so = "}~", io = to.length + so.length, no = new RegExp(`${to}[0-9a-zA-Z_$]{1,${64 - io}}${so}`, "g"), ro = (e3, t2) => e3.replace(no, (e4) => t2.get(e4) || e4), oo = (e3, t2, s2) => e3.replace(no, (e4) => e4 === t2 ? s2 : e4), ao = Symbol("bundleKeys"), lo = { type: "placeholder" };
function ho(e3, t2, s2) {
  return D(e3) ? _e(it(`Invalid pattern "${e3}" for "${t2}", patterns can be neither absolute nor relative paths. If you want your files to be stored in a subdirectory, write its name without a leading slash like this: subdirectory/pattern.`)) : e3.replace(/\[(\w+)(:\d+)?]/g, (e4, i2, n2) => {
    if (!s2.hasOwnProperty(i2) || n2 && "hash" !== i2)
      return _e(it(`"[${i2}${n2 || ""}]" is not a valid placeholder in the "${t2}" pattern.`));
    const r2 = s2[i2](n2 && Number.parseInt(n2.slice(1)));
    return D(r2) ? _e(it(`Invalid substitution "${r2}" for placeholder "[${i2}]" in "${t2}" pattern, can be neither absolute nor relative path.`)) : r2;
  });
}
function co(e3, { [ao]: t2 }) {
  if (!t2.has(e3.toLowerCase()))
    return e3;
  const s2 = P(e3);
  e3 = e3.slice(0, Math.max(0, e3.length - s2.length));
  let i2, n2 = 1;
  for (; t2.has((i2 = e3 + ++n2 + s2).toLowerCase()); )
    ;
  return i2;
}
const uo = /* @__PURE__ */ new Set([".js", ".jsx", ".ts", ".tsx", ".mjs", ".mts", ".cjs", ".cts"]);
function po(e3, t2, s2, i2) {
  const n2 = "function" == typeof t2 ? t2(e3.id) : t2[e3.id];
  return n2 || (s2 ? (i2((r2 = e3.id, o2 = e3.variableName, { code: "MISSING_GLOBAL_NAME", id: r2, message: `No name was provided for external module "${r2}" in "output.globals" – guessing "${o2}".`, names: [o2], url: Ee("configuration-options/#output-globals") })), e3.variableName) : void 0);
  var r2, o2;
}
class fo {
  constructor(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2, c2, u2, d2, p2, f2) {
    this.orderedModules = e3, this.inputOptions = t2, this.outputOptions = s2, this.unsetOptions = i2, this.pluginDriver = n2, this.modulesById = r2, this.chunkByModule = o2, this.externalChunkByModule = a2, this.facadeChunkByModule = l2, this.includedNamespaces = h2, this.manualChunkAlias = c2, this.getPlaceholder = u2, this.bundle = d2, this.inputBase = p2, this.snippets = f2, this.entryModules = [], this.exportMode = "named", this.facadeModule = null, this.namespaceVariableName = "", this.variableName = "", this.accessedGlobalsByScope = /* @__PURE__ */ new Map(), this.dependencies = /* @__PURE__ */ new Set(), this.dynamicEntryModules = [], this.dynamicName = null, this.exportNamesByVariable = /* @__PURE__ */ new Map(), this.exports = /* @__PURE__ */ new Set(), this.exportsByName = /* @__PURE__ */ new Map(), this.fileName = null, this.implicitEntryModules = [], this.implicitlyLoadedBefore = /* @__PURE__ */ new Set(), this.imports = /* @__PURE__ */ new Set(), this.includedDynamicImports = null, this.includedReexportsByModule = /* @__PURE__ */ new Map(), this.isEmpty = true, this.name = null, this.needsExportsShim = false, this.preRenderedChunkInfo = null, this.preliminaryFileName = null, this.renderedChunkInfo = null, this.renderedDependencies = null, this.renderedModules = /* @__PURE__ */ Object.create(null), this.sortedExportNames = null, this.strictFacade = false, this.execIndex = e3.length > 0 ? e3[0].execIndex : 1 / 0;
    const m2 = new Set(e3);
    for (const t3 of e3) {
      o2.set(t3, this), t3.namespace.included && h2.add(t3), this.isEmpty && t3.isIncluded() && (this.isEmpty = false), (t3.info.isEntry || s2.preserveModules) && this.entryModules.push(t3);
      for (const e4 of t3.includedDynamicImporters)
        m2.has(e4) || (this.dynamicEntryModules.push(t3), t3.info.syntheticNamedExports && !s2.preserveModules && (h2.add(t3), this.exports.add(t3.namespace)));
      t3.implicitlyLoadedAfter.size > 0 && this.implicitEntryModules.push(t3);
    }
    this.suggestedVariableName = ht(this.generateVariableName());
  }
  static generateFacade(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2, c2, u2, d2, p2, f2) {
    const m2 = new fo([], e3, t2, s2, i2, n2, r2, o2, a2, l2, null, u2, d2, p2, f2);
    m2.assignFacadeName(c2, h2), a2.has(h2) || a2.set(h2, m2);
    for (const e4 of h2.getDependenciesToBeIncluded())
      m2.dependencies.add(e4 instanceof xr ? r2.get(e4) : o2.get(e4));
    return !m2.dependencies.has(r2.get(h2)) && h2.info.moduleSideEffects && h2.hasEffects() && m2.dependencies.add(r2.get(h2)), m2.ensureReexportsAreAvailableForModule(h2), m2.facadeModule = h2, m2.strictFacade = true, m2;
  }
  canModuleBeFacade(e3, t2) {
    const s2 = e3.getExportNamesByVariable();
    for (const e4 of this.exports)
      if (!s2.has(e4))
        return false;
    for (const i2 of t2)
      if (!s2.has(i2) && i2.module !== e3)
        return false;
    return true;
  }
  finalizeChunk(e3, t2, s2) {
    const i2 = this.getRenderedChunkInfo(), n2 = (e4) => ro(e4, s2), r2 = this.fileName = n2(i2.fileName);
    return { ...i2, code: e3, dynamicImports: i2.dynamicImports.map(n2), fileName: r2, implicitlyLoadedBefore: i2.implicitlyLoadedBefore.map(n2), importedBindings: Object.fromEntries(Object.entries(i2.importedBindings).map(([e4, t3]) => [n2(e4), t3])), imports: i2.imports.map(n2), map: t2, referencedFiles: i2.referencedFiles.map(n2) };
  }
  generateExports() {
    this.sortedExportNames = null;
    const e3 = new Set(this.exports);
    if (null !== this.facadeModule && (false !== this.facadeModule.preserveSignature || this.strictFacade)) {
      const t2 = this.facadeModule.getExportNamesByVariable();
      for (const [s2, i2] of t2) {
        this.exportNamesByVariable.set(s2, [...i2]);
        for (const e4 of i2)
          this.exportsByName.set(e4, s2);
        e3.delete(s2);
      }
    }
    this.outputOptions.minifyInternalExports ? function(e4, t2, s2) {
      let i2 = 0;
      for (const n2 of e4) {
        let [e5] = n2.name;
        if (t2.has(e5))
          do {
            e5 = Ps(++i2), 49 === e5.charCodeAt(0) && (i2 += 9 * 64 ** (e5.length - 1), e5 = Ps(i2));
          } while (ot.has(e5) || t2.has(e5));
        t2.set(e5, n2), s2.set(n2, [e5]);
      }
    }(e3, this.exportsByName, this.exportNamesByVariable) : function(e4, t2, s2) {
      for (const i2 of e4) {
        let e5 = 0, n2 = i2.name;
        for (; t2.has(n2); )
          n2 = i2.name + "$" + ++e5;
        t2.set(n2, i2), s2.set(i2, [n2]);
      }
    }(e3, this.exportsByName, this.exportNamesByVariable), (this.outputOptions.preserveModules || this.facadeModule && this.facadeModule.info.isEntry) && (this.exportMode = Jr(this, this.outputOptions, this.facadeModule.id, this.inputOptions.onwarn));
  }
  generateFacades() {
    var _a2;
    const e3 = [], t2 = /* @__PURE__ */ new Set([...this.entryModules, ...this.implicitEntryModules]), s2 = new Set(this.dynamicEntryModules.map(({ namespace: e4 }) => e4));
    for (const e4 of t2)
      if (e4.preserveSignature)
        for (const t3 of e4.getExportNamesByVariable().keys())
          s2.add(t3);
    for (const i2 of t2) {
      const t3 = Array.from(new Set(i2.chunkNames.filter(({ isUserDefined: e4 }) => e4).map(({ name: e4 }) => e4)), (e4) => ({ name: e4 }));
      if (0 === t3.length && i2.isUserDefinedEntryPoint && t3.push({}), t3.push(...Array.from(i2.chunkFileNames, (e4) => ({ fileName: e4 }))), 0 === t3.length && t3.push({}), !this.facadeModule) {
        const e4 = "strict" === i2.preserveSignature || "exports-only" === i2.preserveSignature && i2.getExportNamesByVariable().size > 0;
        (!e4 || this.outputOptions.preserveModules || this.canModuleBeFacade(i2, s2)) && (this.facadeModule = i2, this.facadeChunkByModule.set(i2, this), i2.preserveSignature && (this.strictFacade = e4), this.assignFacadeName(t3.shift(), i2, this.outputOptions.preserveModules));
      }
      for (const s3 of t3)
        e3.push(fo.generateFacade(this.inputOptions, this.outputOptions, this.unsetOptions, this.pluginDriver, this.modulesById, this.chunkByModule, this.externalChunkByModule, this.facadeChunkByModule, this.includedNamespaces, i2, s3, this.getPlaceholder, this.bundle, this.inputBase, this.snippets));
    }
    for (const e4 of this.dynamicEntryModules)
      e4.info.syntheticNamedExports || (!this.facadeModule && this.canModuleBeFacade(e4, s2) ? (this.facadeModule = e4, this.facadeChunkByModule.set(e4, this), this.strictFacade = true, this.dynamicName = mo(e4)) : this.facadeModule === e4 && !this.strictFacade && this.canModuleBeFacade(e4, s2) ? this.strictFacade = true : ((_a2 = this.facadeChunkByModule.get(e4)) == null ? void 0 : _a2.strictFacade) || (this.includedNamespaces.add(e4), this.exports.add(e4.namespace)));
    return this.outputOptions.preserveModules || this.addNecessaryImportsForFacades(), e3;
  }
  getChunkName() {
    return this.name ?? (this.name = this.outputOptions.sanitizeFileName(this.getFallbackChunkName()));
  }
  getExportNames() {
    return this.sortedExportNames ?? (this.sortedExportNames = [...this.exportsByName.keys()].sort());
  }
  getFileName() {
    return this.fileName || this.getPreliminaryFileName().fileName;
  }
  getImportPath(e3) {
    return R(V(e3, this.getFileName(), "amd" === this.outputOptions.format && !this.outputOptions.amd.forceJsExtensionForImports, true));
  }
  getPreliminaryFileName() {
    var _a2;
    if (this.preliminaryFileName)
      return this.preliminaryFileName;
    let e3, t2 = null;
    const { chunkFileNames: s2, entryFileNames: i2, file: n2, format: r2, preserveModules: o2 } = this.outputOptions;
    if (n2)
      e3 = w(n2);
    else if (null === this.fileName) {
      const [n3, a2] = o2 || ((_a2 = this.facadeModule) == null ? void 0 : _a2.isUserDefinedEntryPoint) ? [i2, "output.entryFileNames"] : [s2, "output.chunkFileNames"];
      e3 = ho("function" == typeof n3 ? n3(this.getPreRenderedChunkInfo()) : n3, a2, { format: () => r2, hash: (e4) => t2 || (t2 = this.getPlaceholder(a2, e4)), name: () => this.getChunkName() }), t2 || (e3 = co(e3, this.bundle));
    } else
      e3 = this.fileName;
    return t2 || (this.bundle[e3] = lo), this.preliminaryFileName = { fileName: e3, hashPlaceholder: t2 };
  }
  getRenderedChunkInfo() {
    return this.renderedChunkInfo ? this.renderedChunkInfo : this.renderedChunkInfo = { ...this.getPreRenderedChunkInfo(), dynamicImports: this.getDynamicDependencies().map(bo), fileName: this.getFileName(), implicitlyLoadedBefore: Array.from(this.implicitlyLoadedBefore, bo), importedBindings: yo(this.getRenderedDependencies(), bo), imports: Array.from(this.dependencies, bo), modules: this.renderedModules, referencedFiles: this.getReferencedFiles() };
  }
  getVariableExportName(e3) {
    return this.outputOptions.preserveModules && e3 instanceof Qn ? "*" : this.exportNamesByVariable.get(e3)[0];
  }
  link() {
    this.dependencies = function(e3, t2, s2, i2) {
      const n2 = [], r2 = /* @__PURE__ */ new Set();
      for (let o3 = t2.length - 1; o3 >= 0; o3--) {
        const a2 = t2[o3];
        if (!r2.has(a2)) {
          const t3 = [];
          eo(a2, t3, r2, e3, s2, i2), n2.unshift(t3);
        }
      }
      const o2 = /* @__PURE__ */ new Set();
      for (const e4 of n2)
        for (const t3 of e4)
          o2.add(t3);
      return o2;
    }(this, this.orderedModules, this.chunkByModule, this.externalChunkByModule);
    for (const e3 of this.orderedModules)
      this.addImplicitlyLoadedBeforeFromModule(e3), this.setUpChunkImportsAndExportsForModule(e3);
  }
  async render() {
    const { dependencies: e3, exportMode: t2, facadeModule: s2, inputOptions: { onwarn: i2 }, outputOptions: n2, pluginDriver: r2, snippets: o2 } = this, { format: a2, hoistTransitiveImports: l2, preserveModules: h2 } = n2;
    if (l2 && !h2 && null !== s2)
      for (const t3 of e3)
        t3 instanceof fo && this.inlineChunkDependencies(t3);
    const c2 = this.getPreliminaryFileName(), { accessedGlobals: u2, indent: d2, magicString: p2, renderedSource: f2, usedModules: m2, usesTopLevelAwait: g2 } = this.renderModules(c2.fileName), y2 = [...this.getRenderedDependencies().values()], x2 = "none" === t2 ? [] : this.getChunkExportDeclarations(a2);
    let b2 = x2.length > 0, E2 = false;
    for (const e4 of y2) {
      const { reexports: t3 } = e4;
      (t3 == null ? void 0 : t3.length) && (b2 = true, !E2 && t3.some((e5) => "default" === e5.reexported) && (E2 = true), "es" === a2 && (e4.reexports = t3.filter(({ reexported: e5 }) => !x2.find(({ exported: t4 }) => t4 === e5))));
    }
    if (!E2) {
      for (const { exported: e4 } of x2)
        if ("default" === e4) {
          E2 = true;
          break;
        }
    }
    const { intro: v2, outro: S2, banner: k2, footer: A2 } = await async function(e4, t3, s3) {
      try {
        let [i4, n3, r3, o3] = await Promise.all([t3.hookReduceValue("banner", e4.banner(s3), [s3], qr), t3.hookReduceValue("footer", e4.footer(s3), [s3], qr), t3.hookReduceValue("intro", e4.intro(s3), [s3], Hr), t3.hookReduceValue("outro", e4.outro(s3), [s3], Hr)]);
        return r3 && (r3 += "\n\n"), o3 && (o3 = `

${o3}`), i4 && (i4 += "\n"), n3 && (n3 = "\n" + n3), { banner: i4, footer: n3, intro: r3, outro: o3 };
      } catch (e5) {
        return _e((i3 = e5.message, { code: "ADDON_ERROR", message: `Could not retrieve "${e5.hook}". Check configuration of plugin "${e5.plugin}".
	Error Message: ${i3}` }));
      }
      var i3;
    }(n2, r2, this.getRenderedChunkInfo());
    return Wr[a2](f2, { accessedGlobals: u2, dependencies: y2, exports: x2, hasDefaultExport: E2, hasExports: b2, id: c2.fileName, indent: d2, intro: v2, isEntryFacade: h2 || null !== s2 && s2.info.isEntry, isModuleFacade: null !== s2, namedExportsMode: "default" !== t2, onwarn: i2, outro: S2, snippets: o2, usesTopLevelAwait: g2 }, n2), k2 && p2.prepend(k2), A2 && p2.append(A2), { chunk: this, magicString: p2, preliminaryFileName: c2, usedModules: m2 };
  }
  addImplicitlyLoadedBeforeFromModule(e3) {
    const { chunkByModule: t2, implicitlyLoadedBefore: s2 } = this;
    for (const i2 of e3.implicitlyLoadedBefore) {
      const e4 = t2.get(i2);
      e4 && e4 !== this && s2.add(e4);
    }
  }
  addNecessaryImportsForFacades() {
    for (const [e3, t2] of this.includedReexportsByModule)
      if (this.includedNamespaces.has(e3))
        for (const e4 of t2)
          this.imports.add(e4);
  }
  assignFacadeName({ fileName: e3, name: t2 }, s2, i2) {
    e3 ? this.fileName = e3 : this.name = this.outputOptions.sanitizeFileName(t2 || (i2 ? this.getPreserveModulesChunkNameFromModule(s2) : mo(s2)));
  }
  checkCircularDependencyImport(e3, t2) {
    var _a2;
    const s2 = e3.module;
    if (s2 instanceof xr) {
      const l2 = this.chunkByModule.get(s2);
      let h2;
      do {
        if (h2 = t2.alternativeReexportModules.get(e3), h2) {
          this.chunkByModule.get(h2) !== l2 && this.inputOptions.onwarn((i2 = ((_a2 = s2.getExportNamesByVariable().get(e3)) == null ? void 0 : _a2[0]) || "*", n2 = s2.id, r2 = h2.id, o2 = t2.id, a2 = this.outputOptions.preserveModules, { code: "CYCLIC_CROSS_CHUNK_REEXPORT", exporter: n2, id: o2, message: `Export "${i2}" of module "${O(n2)}" was reexported through module "${O(r2)}" while both modules are dependencies of each other and will end up in different chunks by current Rollup settings. This scenario is not well supported at the moment as it will produce a circular dependency between chunks and will likely lead to broken execution order.
Either change the import in "${O(o2)}" to point directly to the exporting module or ${a2 ? 'do not use "output.preserveModules"' : 'reconfigure "output.manualChunks"'} to ensure these modules end up in the same chunk.`, reexporter: r2 })), t2 = h2;
        }
      } while (h2);
    }
    var i2, n2, r2, o2, a2;
  }
  ensureReexportsAreAvailableForModule(e3) {
    const t2 = [], s2 = e3.getExportNamesByVariable();
    for (const i2 of s2.keys()) {
      const s3 = i2 instanceof Jn, n2 = s3 ? i2.getBaseVariable() : i2;
      if (this.checkCircularDependencyImport(n2, e3), !(n2 instanceof Qn && this.outputOptions.preserveModules)) {
        const e4 = n2.module;
        if (e4 instanceof xr) {
          const i3 = this.chunkByModule.get(e4);
          i3 && i3 !== this && (i3.exports.add(n2), t2.push(n2), s3 && this.imports.add(n2));
        }
      }
    }
    t2.length > 0 && this.includedReexportsByModule.set(e3, t2);
  }
  generateVariableName() {
    if (this.manualChunkAlias)
      return this.manualChunkAlias;
    const e3 = this.entryModules[0] || this.implicitEntryModules[0] || this.dynamicEntryModules[0] || this.orderedModules[this.orderedModules.length - 1];
    return e3 ? mo(e3) : "chunk";
  }
  getChunkExportDeclarations(e3) {
    const t2 = [];
    for (const s2 of this.getExportNames()) {
      if ("*" === s2[0])
        continue;
      const i2 = this.exportsByName.get(s2);
      if (!(i2 instanceof Jn)) {
        const t3 = i2.module;
        if (t3) {
          const i3 = this.chunkByModule.get(t3);
          if (i3 !== this) {
            if (!i3 || "es" !== e3)
              continue;
            const t4 = this.renderedDependencies.get(i3);
            if (!t4)
              continue;
            const { imports: n3, reexports: r3 } = t4, o3 = r3 == null ? void 0 : r3.find(({ reexported: e4 }) => e4 === s2), a2 = n3 == null ? void 0 : n3.find(({ imported: e4 }) => e4 === (o3 == null ? void 0 : o3.imported));
            if (!a2)
              continue;
          }
        }
      }
      let n2 = null, r2 = false, o2 = i2.getName(this.snippets.getPropertyAccess);
      if (i2 instanceof Is) {
        for (const e4 of i2.declarations)
          if (e4.parent instanceof zi || e4 instanceof ji && e4.declaration instanceof zi) {
            r2 = true;
            break;
          }
      } else
        i2 instanceof Jn && (n2 = o2, "es" === e3 && (o2 = i2.renderName));
      t2.push({ exported: s2, expression: n2, hoisted: r2, local: o2 });
    }
    return t2;
  }
  getDependenciesToBeDeconflicted(e3, t2, s2) {
    const i2 = /* @__PURE__ */ new Set(), n2 = /* @__PURE__ */ new Set(), r2 = /* @__PURE__ */ new Set();
    for (const t3 of [...this.exportNamesByVariable.keys(), ...this.imports])
      if (e3 || t3.isNamespace) {
        const o2 = t3.module;
        if (o2 instanceof ct) {
          const a2 = this.externalChunkByModule.get(o2);
          i2.add(a2), e3 && ("default" === t3.name ? sn[s2(o2.id)] && n2.add(a2) : "*" === t3.name && rn[s2(o2.id)] && r2.add(a2));
        } else {
          const s3 = this.chunkByModule.get(o2);
          s3 !== this && (i2.add(s3), e3 && "default" === s3.exportMode && t3.isNamespace && r2.add(s3));
        }
      }
    if (t2)
      for (const e4 of this.dependencies)
        i2.add(e4);
    return { deconflictedDefault: n2, deconflictedNamespace: r2, dependencies: i2 };
  }
  getDynamicDependencies() {
    return this.getIncludedDynamicImports().map((e3) => e3.facadeChunk || e3.chunk || e3.externalChunk || e3.resolution).filter((e3) => e3 !== this && (e3 instanceof fo || e3 instanceof B));
  }
  getDynamicImportStringAndAssertions(e3, t2) {
    if (e3 instanceof ct) {
      const s2 = this.externalChunkByModule.get(e3);
      return [`'${s2.getImportPath(t2)}'`, s2.getImportAssertions(this.snippets)];
    }
    return [e3 || "", "es" === this.outputOptions.format && this.outputOptions.externalImportAssertions || null];
  }
  getFallbackChunkName() {
    return this.manualChunkAlias ? this.manualChunkAlias : this.dynamicName ? this.dynamicName : this.fileName ? M(this.fileName) : M(this.orderedModules[this.orderedModules.length - 1].id);
  }
  getImportSpecifiers() {
    const { interop: e3 } = this.outputOptions, t2 = /* @__PURE__ */ new Map();
    for (const s2 of this.imports) {
      const i2 = s2.module;
      let n2, r2;
      if (i2 instanceof ct) {
        if (n2 = this.externalChunkByModule.get(i2), r2 = s2.name, "default" !== r2 && "*" !== r2 && "defaultOnly" === e3(i2.id))
          return _e(tt(i2.id, r2, false));
      } else
        n2 = this.chunkByModule.get(i2), r2 = n2.getVariableExportName(s2);
      F(t2, n2, j).push({ imported: r2, local: s2.getName(this.snippets.getPropertyAccess) });
    }
    return t2;
  }
  getIncludedDynamicImports() {
    if (this.includedDynamicImports)
      return this.includedDynamicImports;
    const e3 = [];
    for (const t2 of this.orderedModules)
      for (const { node: s2, resolution: i2 } of t2.dynamicImports)
        s2.included && e3.push(i2 instanceof xr ? { chunk: this.chunkByModule.get(i2), externalChunk: null, facadeChunk: this.facadeChunkByModule.get(i2), node: s2, resolution: i2 } : i2 instanceof ct ? { chunk: null, externalChunk: this.externalChunkByModule.get(i2), facadeChunk: null, node: s2, resolution: i2 } : { chunk: null, externalChunk: null, facadeChunk: null, node: s2, resolution: i2 });
    return this.includedDynamicImports = e3;
  }
  getPreRenderedChunkInfo() {
    if (this.preRenderedChunkInfo)
      return this.preRenderedChunkInfo;
    const { dynamicEntryModules: e3, facadeModule: t2, implicitEntryModules: s2, orderedModules: i2 } = this;
    return this.preRenderedChunkInfo = { exports: this.getExportNames(), facadeModuleId: t2 && t2.id, isDynamicEntry: e3.length > 0, isEntry: !!(t2 == null ? void 0 : t2.info.isEntry), isImplicitEntry: s2.length > 0, moduleIds: i2.map(({ id: e4 }) => e4), name: this.getChunkName(), type: "chunk" };
  }
  getPreserveModulesChunkNameFromModule(e3) {
    const t2 = go(e3);
    if (t2)
      return t2;
    const { preserveModulesRoot: s2, sanitizeFileName: i2 } = this.outputOptions, n2 = i2(A(e3.id.split(xo, 1)[0])), r2 = P(n2), o2 = uo.has(r2) ? n2.slice(0, -r2.length) : n2;
    return S(o2) ? s2 && $(o2).startsWith(s2) ? o2.slice(s2.length).replace(/^[/\\]/, "") : C(this.inputBase, o2) : `_virtual/${w(o2)}`;
  }
  getReexportSpecifiers() {
    const { externalLiveBindings: e3, interop: t2 } = this.outputOptions, s2 = /* @__PURE__ */ new Map();
    for (let i2 of this.getExportNames()) {
      let n2, r2, o2 = false;
      if ("*" === i2[0]) {
        const s3 = i2.slice(1);
        "defaultOnly" === t2(s3) && this.inputOptions.onwarn(st(s3)), o2 = e3, n2 = this.externalChunkByModule.get(this.modulesById.get(s3)), r2 = i2 = "*";
      } else {
        const s3 = this.exportsByName.get(i2);
        if (s3 instanceof Jn)
          continue;
        const a2 = s3.module;
        if (a2 instanceof xr) {
          if (n2 = this.chunkByModule.get(a2), n2 === this)
            continue;
          r2 = n2.getVariableExportName(s3), o2 = s3.isReassigned;
        } else {
          if (n2 = this.externalChunkByModule.get(a2), r2 = s3.name, "default" !== r2 && "*" !== r2 && "defaultOnly" === t2(a2.id))
            return _e(tt(a2.id, r2, true));
          o2 = e3 && ("default" !== r2 || nn(t2(a2.id), true));
        }
      }
      F(s2, n2, j).push({ imported: r2, needsLiveBinding: o2, reexported: i2 });
    }
    return s2;
  }
  getReferencedFiles() {
    const e3 = /* @__PURE__ */ new Set();
    for (const t2 of this.orderedModules)
      for (const s2 of t2.importMetas) {
        const t3 = s2.getReferencedFileName(this.pluginDriver);
        t3 && e3.add(t3);
      }
    return [...e3];
  }
  getRenderedDependencies() {
    if (this.renderedDependencies)
      return this.renderedDependencies;
    const e3 = this.getImportSpecifiers(), t2 = this.getReexportSpecifiers(), s2 = /* @__PURE__ */ new Map(), i2 = this.getFileName();
    for (const n2 of this.dependencies) {
      const r2 = e3.get(n2) || null, o2 = t2.get(n2) || null, a2 = n2 instanceof B || "default" !== n2.exportMode, l2 = n2.getImportPath(i2);
      s2.set(n2, { assertions: n2 instanceof B ? n2.getImportAssertions(this.snippets) : null, defaultVariableName: n2.defaultVariableName, globalName: n2 instanceof B && ("umd" === this.outputOptions.format || "iife" === this.outputOptions.format) && po(n2, this.outputOptions.globals, null !== (r2 || o2), this.inputOptions.onwarn), importPath: l2, imports: r2, isChunk: n2 instanceof fo, name: n2.variableName, namedExportsMode: a2, namespaceVariableName: n2.namespaceVariableName, reexports: o2 });
    }
    return this.renderedDependencies = s2;
  }
  inlineChunkDependencies(e3) {
    for (const t2 of e3.dependencies)
      this.dependencies.has(t2) || (this.dependencies.add(t2), t2 instanceof fo && this.inlineChunkDependencies(t2));
  }
  renderModules(e3) {
    const { accessedGlobalsByScope: t2, dependencies: s2, exportNamesByVariable: i2, includedNamespaces: n2, inputOptions: { onwarn: r2 }, isEmpty: o2, orderedModules: h2, outputOptions: p2, pluginDriver: f2, renderedModules: y2, snippets: x2 } = this, { compact: b2, dynamicImportFunction: E2, format: v2, freeze: S2, namespaceToStringTag: k2, preserveModules: A2 } = p2, { _: w2, cnst: I2, n: P2 } = x2;
    this.setDynamicImportResolutions(e3), this.setImportMetaResolutions(e3), this.setIdentifierRenderResolutions();
    const C2 = new class e4 {
      constructor(e5 = {}) {
        this.intro = e5.intro || "", this.separator = void 0 !== e5.separator ? e5.separator : "\n", this.sources = [], this.uniqueSources = [], this.uniqueSourceIndexByFilename = {};
      }
      addSource(e5) {
        if (e5 instanceof m)
          return this.addSource({ content: e5, filename: e5.filename, separator: this.separator });
        if (!c(e5) || !e5.content)
          throw new Error("bundle.addSource() takes an object with a `content` property, which should be an instance of MagicString, and an optional `filename`");
        if (["filename", "indentExclusionRanges", "separator"].forEach((t3) => {
          g.call(e5, t3) || (e5[t3] = e5.content[t3]);
        }), void 0 === e5.separator && (e5.separator = this.separator), e5.filename)
          if (g.call(this.uniqueSourceIndexByFilename, e5.filename)) {
            const t3 = this.uniqueSources[this.uniqueSourceIndexByFilename[e5.filename]];
            if (e5.content.original !== t3.content)
              throw new Error(`Illegal source: same filename (${e5.filename}), different contents`);
          } else
            this.uniqueSourceIndexByFilename[e5.filename] = this.uniqueSources.length, this.uniqueSources.push({ filename: e5.filename, content: e5.content.original });
        return this.sources.push(e5), this;
      }
      append(e5, t3) {
        return this.addSource({ content: new m(e5), separator: t3 && t3.separator || "" }), this;
      }
      clone() {
        const t3 = new e4({ intro: this.intro, separator: this.separator });
        return this.sources.forEach((e5) => {
          t3.addSource({ filename: e5.filename, content: e5.content.clone(), separator: e5.separator });
        }), t3;
      }
      generateDecodedMap(e5 = {}) {
        const t3 = [];
        this.sources.forEach((e6) => {
          Object.keys(e6.content.storedNames).forEach((e7) => {
            ~t3.indexOf(e7) || t3.push(e7);
          });
        });
        const s3 = new d(e5.hires);
        return this.intro && s3.advance(this.intro), this.sources.forEach((e6, i3) => {
          i3 > 0 && s3.advance(this.separator);
          const n3 = e6.filename ? this.uniqueSourceIndexByFilename[e6.filename] : -1, r3 = e6.content, o3 = u(r3.original);
          r3.intro && s3.advance(r3.intro), r3.firstChunk.eachNext((i4) => {
            const a2 = o3(i4.start);
            i4.intro.length && s3.advance(i4.intro), e6.filename ? i4.edited ? s3.addEdit(n3, i4.content, a2, i4.storeName ? t3.indexOf(i4.original) : -1) : s3.addUneditedChunk(n3, i4, r3.original, a2, r3.sourcemapLocations) : s3.advance(i4.content), i4.outro.length && s3.advance(i4.outro);
          }), r3.outro && s3.advance(r3.outro);
        }), { file: e5.file ? e5.file.split(/[/\\]/).pop() : null, sources: this.uniqueSources.map((t4) => e5.file ? l(e5.file, t4.filename) : t4.filename), sourcesContent: this.uniqueSources.map((t4) => e5.includeContent ? t4.content : null), names: t3, mappings: s3.raw };
      }
      generateMap(e5) {
        return new a(this.generateDecodedMap(e5));
      }
      getIndentString() {
        const e5 = {};
        return this.sources.forEach((t3) => {
          const s3 = t3.content._getRawIndentString();
          null !== s3 && (e5[s3] || (e5[s3] = 0), e5[s3] += 1);
        }), Object.keys(e5).sort((t3, s3) => e5[t3] - e5[s3])[0] || "	";
      }
      indent(e5) {
        if (arguments.length || (e5 = this.getIndentString()), "" === e5)
          return this;
        let t3 = !this.intro || "\n" === this.intro.slice(-1);
        return this.sources.forEach((s3, i3) => {
          const n3 = void 0 !== s3.separator ? s3.separator : this.separator, r3 = t3 || i3 > 0 && /\r?\n$/.test(n3);
          s3.content.indent(e5, { exclude: s3.indentExclusionRanges, indentStart: r3 }), t3 = "\n" === s3.content.lastChar();
        }), this.intro && (this.intro = e5 + this.intro.replace(/^[^\n]/gm, (t4, s3) => s3 > 0 ? e5 + t4 : t4)), this;
      }
      prepend(e5) {
        return this.intro = e5 + this.intro, this;
      }
      toString() {
        const e5 = this.sources.map((e6, t3) => {
          const s3 = void 0 !== e6.separator ? e6.separator : this.separator;
          return (t3 > 0 ? s3 : "") + e6.content.toString();
        }).join("");
        return this.intro + e5;
      }
      isEmpty() {
        return !(this.intro.length && this.intro.trim() || this.sources.some((e5) => !e5.content.isEmpty()));
      }
      length() {
        return this.sources.reduce((e5, t3) => e5 + t3.content.length(), this.intro.length);
      }
      trimLines() {
        return this.trim("[\\r\\n]");
      }
      trim(e5) {
        return this.trimStart(e5).trimEnd(e5);
      }
      trimStart(e5) {
        const t3 = new RegExp("^" + (e5 || "\\s") + "+");
        if (this.intro = this.intro.replace(t3, ""), !this.intro) {
          let t4, s3 = 0;
          do {
            if (t4 = this.sources[s3++], !t4)
              break;
          } while (!t4.content.trimStartAborted(e5));
        }
        return this;
      }
      trimEnd(e5) {
        const t3 = new RegExp((e5 || "\\s") + "+$");
        let s3, i3 = this.sources.length - 1;
        do {
          if (s3 = this.sources[i3--], !s3) {
            this.intro = this.intro.replace(t3, "");
            break;
          }
        } while (!s3.content.trimEndAborted(e5));
        return this;
      }
    }({ separator: `${P2}${P2}` }), $2 = function(e4, t3) {
      if (true !== t3.indent)
        return t3.indent;
      for (const t4 of e4) {
        const e5 = Zr(t4.originalCode);
        if (null !== e5)
          return e5;
      }
      return "	";
    }(h2, p2), N2 = [];
    let _2 = "";
    const T2 = /* @__PURE__ */ new Set(), R2 = /* @__PURE__ */ new Map(), M2 = { dynamicImportFunction: E2, exportNamesByVariable: i2, format: v2, freeze: S2, indent: $2, namespaceToStringTag: k2, pluginDriver: f2, snippets: x2, useOriginalName: null };
    let O2 = false;
    for (const e4 of h2) {
      let s3, i3 = 0;
      if (e4.isIncluded() || n2.has(e4)) {
        const r4 = e4.render(M2);
        ({ source: s3 } = r4), O2 || (O2 = r4.usesTopLevelAwait), i3 = s3.length(), i3 && (b2 && s3.lastLine().includes("//") && s3.append("\n"), R2.set(e4, s3), C2.addSource(s3), N2.push(e4));
        const o4 = e4.namespace;
        if (n2.has(e4) && !A2) {
          const e5 = o4.renderBlock(M2);
          o4.renderFirst() ? _2 += P2 + e5 : C2.addSource(new m(e5));
        }
        const a2 = t2.get(e4.scope);
        if (a2)
          for (const e5 of a2)
            T2.add(e5);
      }
      const { renderedExports: r3, removedExports: o3 } = e4.getRenderedExports();
      y2[e4.id] = { get code() {
        return (s3 == null ? void 0 : s3.toString()) ?? null;
      }, originalLength: e4.originalCode.length, removedExports: o3, renderedExports: r3, renderedLength: i3 };
    }
    _2 && C2.prepend(_2 + P2 + P2), this.needsExportsShim && C2.prepend(`${P2}${I2} ${Yn}${w2}=${w2}void 0;${P2}${P2}`);
    const D2 = b2 ? C2 : C2.trim();
    var L2;
    return o2 && 0 === this.getExportNames().length && 0 === s2.size && r2({ code: "EMPTY_BUNDLE", message: `Generated an empty chunk: "${L2 = this.getChunkName()}".`, names: [L2] }), { accessedGlobals: T2, indent: $2, magicString: C2, renderedSource: D2, usedModules: N2, usesTopLevelAwait: O2 };
  }
  setDynamicImportResolutions(e3) {
    const { accessedGlobalsByScope: t2, outputOptions: s2, pluginDriver: i2, snippets: n2 } = this;
    for (const r2 of this.getIncludedDynamicImports())
      if (r2.chunk) {
        const { chunk: o2, facadeChunk: a2, node: l2, resolution: h2 } = r2;
        o2 === this ? l2.setInternalResolution(h2.namespace) : l2.setExternalResolution((a2 || o2).exportMode, h2, s2, n2, i2, t2, `'${(a2 || o2).getImportPath(e3)}'`, !(a2 == null ? void 0 : a2.strictFacade) && o2.exportNamesByVariable.get(h2.namespace)[0], null);
      } else {
        const { node: o2, resolution: a2 } = r2, [l2, h2] = this.getDynamicImportStringAndAssertions(a2, e3);
        o2.setExternalResolution("external", a2, s2, n2, i2, t2, l2, false, h2);
      }
  }
  setIdentifierRenderResolutions() {
    const { format: e3, interop: t2, namespaceToStringTag: s2, preserveModules: i2, externalLiveBindings: n2 } = this.outputOptions, r2 = /* @__PURE__ */ new Set();
    for (const t3 of this.getExportNames()) {
      const s3 = this.exportsByName.get(t3);
      "es" !== e3 && "system" !== e3 && s3.isReassigned && !s3.isId ? s3.setRenderNames("exports", t3) : s3 instanceof Jn ? r2.add(s3) : s3.setRenderNames(null, null);
    }
    for (const e4 of this.orderedModules)
      if (e4.needsExportShim) {
        this.needsExportsShim = true;
        break;
      }
    const o2 = /* @__PURE__ */ new Set(["Object", "Promise"]);
    switch (this.needsExportsShim && o2.add(Yn), s2 && o2.add("Symbol"), e3) {
      case "system":
        o2.add("module").add("exports");
        break;
      case "es":
        break;
      case "cjs":
        o2.add("module").add("require").add("__filename").add("__dirname");
      default:
        o2.add("exports");
        for (const e4 of vn)
          o2.add(e4);
    }
    Yr(this.orderedModules, this.getDependenciesToBeDeconflicted("es" !== e3 && "system" !== e3, "amd" === e3 || "umd" === e3 || "iife" === e3, t2), this.imports, o2, e3, t2, i2, n2, this.chunkByModule, this.externalChunkByModule, r2, this.exportNamesByVariable, this.accessedGlobalsByScope, this.includedNamespaces);
  }
  setImportMetaResolutions(e3) {
    const { accessedGlobalsByScope: t2, includedNamespaces: s2, orderedModules: i2, outputOptions: { format: n2, preserveModules: r2 } } = this;
    for (const o2 of i2) {
      for (const s3 of o2.importMetas)
        s3.setResolution(n2, t2, e3);
      s2.has(o2) && !r2 && o2.namespace.prepare(t2);
    }
  }
  setUpChunkImportsAndExportsForModule(e3) {
    const t2 = new Set(e3.includedImports);
    if (!this.outputOptions.preserveModules && this.includedNamespaces.has(e3)) {
      const s2 = e3.namespace.getMemberVariables();
      for (const e4 of Object.values(s2))
        t2.add(e4);
    }
    for (let s2 of t2) {
      s2 instanceof Un && (s2 = s2.getOriginalVariable()), s2 instanceof Jn && (s2 = s2.getBaseVariable());
      const t3 = this.chunkByModule.get(s2.module);
      t3 !== this && (this.imports.add(s2), s2.module instanceof xr && (this.checkCircularDependencyImport(s2, e3), s2 instanceof Qn && this.outputOptions.preserveModules || t3.exports.add(s2)));
    }
    (this.includedNamespaces.has(e3) || e3.info.isEntry && false !== e3.preserveSignature || e3.includedDynamicImporters.some((e4) => this.chunkByModule.get(e4) !== this)) && this.ensureReexportsAreAvailableForModule(e3);
    for (const { node: t3, resolution: s2 } of e3.dynamicImports)
      t3.included && s2 instanceof xr && this.chunkByModule.get(s2) === this && !this.includedNamespaces.has(s2) && (this.includedNamespaces.add(s2), this.ensureReexportsAreAvailableForModule(s2));
  }
}
function mo(e3) {
  return go(e3) ?? M(e3.id);
}
function go(e3) {
  var _a2, _b;
  return ((_a2 = e3.chunkNames.find(({ isUserDefined: e4 }) => e4)) == null ? void 0 : _a2.name) ?? ((_b = e3.chunkNames[0]) == null ? void 0 : _b.name);
}
function yo(e3, t2) {
  const s2 = {};
  for (const [i2, n2] of e3) {
    const e4 = /* @__PURE__ */ new Set();
    if (n2.imports)
      for (const { imported: t3 } of n2.imports)
        e4.add(t3);
    if (n2.reexports)
      for (const { imported: t3 } of n2.reexports)
        e4.add(t3);
    s2[t2(i2)] = [...e4];
  }
  return s2;
}
const xo = /[#?]/, bo = (e3) => e3.getFileName();
function* Eo(e3) {
  for (const t2 of e3)
    yield* t2;
}
function vo(e3, t2, s2) {
  const { chunkDefinitions: i2, modulesInManualChunks: n2 } = function(e4) {
    const t3 = [], s3 = new Set(e4.keys()), i3 = /* @__PURE__ */ Object.create(null);
    for (const [t4, n3] of e4)
      So(t4, i3[n3] || (i3[n3] = []), s3);
    for (const [e5, s4] of Object.entries(i3))
      t3.push({ alias: e5, modules: s4 });
    return { chunkDefinitions: t3, modulesInManualChunks: s3 };
  }(t2), { allEntries: r2, dependentEntriesByModule: o2, dynamicallyDependentEntriesByDynamicEntry: a2, dynamicImportsByEntry: l2 } = function(e4) {
    const t3 = /* @__PURE__ */ new Set(), s3 = /* @__PURE__ */ new Map(), i3 = [], n3 = new Set(e4);
    let r3 = 0;
    for (const e5 of n3) {
      const o4 = /* @__PURE__ */ new Set();
      i3.push(o4);
      const a4 = /* @__PURE__ */ new Set([e5]);
      for (const e6 of a4) {
        F(s3, e6, z).add(r3);
        for (const t4 of e6.getDependenciesToBeIncluded())
          t4 instanceof ct || a4.add(t4);
        for (const { resolution: s4 } of e6.dynamicImports)
          s4 instanceof xr && s4.includedDynamicImporters.length > 0 && !n3.has(s4) && (t3.add(s4), n3.add(s4), o4.add(s4));
        for (const s4 of e6.implicitlyLoadedBefore)
          n3.has(s4) || (t3.add(s4), n3.add(s4));
      }
      r3++;
    }
    const o3 = [...n3], { dynamicEntries: a3, dynamicImportsByEntry: l3 } = function(e5, t4, s4) {
      const i4 = /* @__PURE__ */ new Map(), n4 = /* @__PURE__ */ new Set();
      for (const [s5, r5] of e5.entries())
        i4.set(r5, s5), t4.has(r5) && n4.add(s5);
      const r4 = [];
      for (const e6 of s4) {
        const t5 = /* @__PURE__ */ new Set();
        for (const s5 of e6)
          t5.add(i4.get(s5));
        r4.push(t5);
      }
      return { dynamicEntries: n4, dynamicImportsByEntry: r4 };
    }(o3, t3, i3);
    return { allEntries: o3, dependentEntriesByModule: s3, dynamicallyDependentEntriesByDynamicEntry: ko(s3, a3, o3), dynamicImportsByEntry: l3 };
  }(e3), h2 = Object.values(Ao(function* (e4, t3) {
    for (const [s3, i3] of e4)
      t3.has(s3) || (yield { dependentEntries: i3, modules: [s3] });
  }(o2, n2)));
  return function(e4, t3, s3, i3) {
    const n3 = i3.map(() => 0n), r3 = i3.map((e5, s4) => t3.has(s4) ? -1n : 0n);
    let o3 = 1n;
    for (const { dependentEntries: t4 } of e4) {
      for (const e5 of t4)
        n3[e5] |= o3;
      o3 <<= 1n;
    }
    const a3 = t3;
    for (const [e5, t4] of a3) {
      a3.delete(e5);
      const i4 = r3[e5];
      let o4 = i4;
      for (const e6 of t4)
        o4 &= n3[e6] | r3[e6];
      if (o4 !== i4) {
        r3[e5] = o4;
        for (const t5 of s3[e5])
          F(a3, t5, z).add(e5);
      }
    }
    o3 = 1n;
    for (const { dependentEntries: t4 } of e4) {
      for (const e5 of t4)
        (r3[e5] & o3) === o3 && t4.delete(e5);
      o3 <<= 1n;
    }
  }(h2, a2, l2, r2), i2.push(...function(e4, t3, s3) {
    return 0 === s3 ? Object.values(t3).map(({ modules: e5 }) => ({ alias: null, modules: e5 })) : function(e5, t4, s4) {
      ur("optimize chunks", 3);
      const i3 = function(e6, t5, s5) {
        const i4 = [], n3 = [], r3 = /* @__PURE__ */ new Map(), o3 = [];
        for (let e7 = 0; e7 < t5; e7++)
          o3.push(/* @__PURE__ */ new Set());
        for (const [t6, { dependentEntries: a3, modules: l3 }] of Object.entries(e6)) {
          const e7 = { correlatedSideEffects: /* @__PURE__ */ new Set(), dependencies: /* @__PURE__ */ new Set(), dependentChunks: /* @__PURE__ */ new Set(), dependentEntries: a3, modules: l3, pure: true, sideEffects: /* @__PURE__ */ new Set(), size: 0 };
          let h3 = 0, c2 = true;
          for (const t7 of l3)
            r3.set(t7, e7), c2 && (c2 = !t7.hasEffects()), h3 += t7.originalCode.length;
          if (e7.pure = c2, e7.size = h3, !c2) {
            for (const e8 of a3)
              o3[e8].add(t6);
            e7.sideEffects.add(t6);
          }
          (h3 < s5 ? i4 : n3).push(e7);
        }
        return function(e7, t6, s6) {
          for (const i5 of e7) {
            i5.sort(wo);
            for (const e8 of i5) {
              const { dependencies: i6, modules: n4, correlatedSideEffects: r4, dependentEntries: o4 } = e8;
              for (const s7 of n4)
                for (const n5 of s7.getDependenciesToBeIncluded()) {
                  const s8 = t6.get(n5);
                  s8 && s8 !== e8 && (i6.add(s8), s8.dependentChunks.add(e8));
                }
              let a3 = true;
              for (const e9 of o4) {
                const t7 = s6[e9];
                if (a3) {
                  for (const e10 of t7)
                    r4.add(e10);
                  a3 = false;
                } else
                  for (const e10 of r4)
                    t7.has(e10) || r4.delete(e10);
              }
            }
          }
        }([n3, i4], r3, o3), { big: new Set(n3), small: new Set(i4) };
      }(e5, t4, s4);
      console.log("Before eliminating small chunks, there were\n", Object.keys(e5).length, "chunks, of which\n", i3.small.size, "were below minChunkSize."), i3.small.size > 0 && function(e6, t5) {
        for (const s5 of [false, true])
          for (const i4 of e6.small) {
            let n3 = null, r3 = 1 / 0;
            const { modules: o3, pure: a3, size: l3 } = i4;
            for (const o4 of Eo([e6.small, e6.big])) {
              if (i4 === o4)
                continue;
              const e7 = !s5 && o4.size >= t5, a4 = $o(i4, o4, e7);
              a4 < r3 && Io(i4, o4, e7) && (n3 = o4, r3 = a4);
            }
            if (n3) {
              e6.small.delete(i4), Co(n3, t5, e6).delete(n3), n3.modules.push(...o3), n3.size += l3, n3.pure && (n3.pure = a3);
              const { correlatedSideEffects: s6, dependencies: r4, dependentChunks: h3, dependentEntries: c2, sideEffects: u2 } = n3;
              for (const e7 of s6)
                i4.correlatedSideEffects.has(e7) || s6.delete(e7);
              for (const e7 of i4.dependentEntries)
                c2.add(e7);
              for (const e7 of i4.sideEffects)
                u2.add(e7);
              for (const e7 of i4.dependencies)
                r4.add(e7), e7.dependentChunks.delete(i4), e7.dependentChunks.add(n3);
              for (const e7 of i4.dependentChunks)
                h3.add(e7), e7.dependencies.delete(i4), e7.dependencies.add(n3);
              r4.delete(n3), h3.delete(n3), Co(n3, t5, e6).add(n3);
            }
          }
      }(i3, s4);
      return console.log("After merging chunks,\n", i3.small.size + i3.big.size, "chunks remain, of which\n", i3.small.size, "are below minChunkSize."), dr("optimize chunks", 3), [...i3.small, ...i3.big];
    }(t3, e4.length, s3).map(({ modules: e5 }) => ({ alias: null, modules: e5 }));
  }(r2, Ao(h2), s2)), i2;
}
function So(e3, t2, s2) {
  const i2 = /* @__PURE__ */ new Set([e3]);
  for (const e4 of i2) {
    s2.add(e4), t2.push(e4);
    for (const t3 of e4.dependencies)
      t3 instanceof ct || s2.has(t3) || i2.add(t3);
  }
}
function ko(e3, t2, s2) {
  const i2 = /* @__PURE__ */ new Map();
  for (const n2 of t2) {
    const t3 = F(i2, n2, z), r2 = s2[n2];
    for (const s3 of Eo([r2.includedDynamicImporters, r2.implicitlyLoadedAfter]))
      for (const i3 of e3.get(s3))
        t3.add(i3);
  }
  return i2;
}
function Ao(e3) {
  var t2;
  const s2 = /* @__PURE__ */ Object.create(null);
  for (const { dependentEntries: i2, modules: n2 } of e3) {
    let e4 = 0n;
    for (const t3 of i2)
      e4 |= 1n << BigInt(t3);
    (s2[t2 = String(e4)] || (s2[t2] = { dependentEntries: new Set(i2), modules: [] })).modules.push(...n2);
  }
  return s2;
}
function wo({ size: e3 }, { size: t2 }) {
  return e3 - t2;
}
function Io(e3, t2, s2) {
  return !(Po(e3, t2, true) || Po(t2, e3, !s2));
}
function Po(e3, t2, s2) {
  const { correlatedSideEffects: i2 } = t2;
  if (s2) {
    for (const t3 of e3.sideEffects)
      if (!i2.has(t3))
        return true;
  }
  const n2 = new Set(e3.dependencies);
  for (const { dependencies: e4, sideEffects: r2 } of n2) {
    for (const s3 of e4) {
      if (s3 === t2)
        return true;
      n2.add(s3);
    }
    if (s2) {
      for (const e5 of r2)
        if (!i2.has(e5))
          return true;
    }
  }
  return false;
}
function Co(e3, t2, s2) {
  return e3.size < t2 ? s2.small : s2.big;
}
function $o({ dependentEntries: e3 }, { dependentEntries: t2 }, s2) {
  let i2 = 0;
  for (const s3 of t2)
    e3.has(s3) || i2++;
  for (const n2 of e3)
    if (!t2.has(n2)) {
      if (s2)
        return 1 / 0;
      i2++;
    }
  return i2;
}
const No = (e3, t2) => e3.execIndex > t2.execIndex ? 1 : -1;
function _o(e3, t2, s2) {
  const i2 = Symbol(e3.id), n2 = [e3.id];
  let r2 = t2;
  for (e3.cycles.add(i2); r2 !== e3; )
    r2.cycles.add(i2), n2.push(r2.id), r2 = s2.get(r2);
  return n2.push(n2[0]), n2.reverse(), n2;
}
const To = (e3, t2) => t2 ? `(${e3})` : e3, Ro = /^(?!\d)[\w$]+$/;
class Mo {
  constructor(e3, t2) {
    this.isOriginal = true, this.filename = e3, this.content = t2;
  }
  traceSegment(e3, t2, s2) {
    return { column: t2, line: e3, name: s2, source: this };
  }
}
class Oo {
  constructor(e3, t2) {
    this.sources = t2, this.names = e3.names, this.mappings = e3.mappings;
  }
  traceMappings() {
    const e3 = [], t2 = /* @__PURE__ */ new Map(), s2 = [], i2 = [], n2 = /* @__PURE__ */ new Map(), r2 = [];
    for (const o2 of this.mappings) {
      const a2 = [];
      for (const r3 of o2) {
        if (1 === r3.length)
          continue;
        const o3 = this.sources[r3[1]];
        if (!o3)
          continue;
        const l2 = o3.traceSegment(r3[2], r3[3], 5 === r3.length ? this.names[r3[4]] : "");
        if (l2) {
          const { column: o4, line: h2, name: c2, source: { content: u2, filename: d2 } } = l2;
          let p2 = t2.get(d2);
          if (void 0 === p2)
            p2 = e3.length, e3.push(d2), t2.set(d2, p2), s2[p2] = u2;
          else if (null == s2[p2])
            s2[p2] = u2;
          else if (null != u2 && s2[p2] !== u2)
            return _e(et(d2));
          const f2 = [r3[0], p2, h2, o4];
          if (c2) {
            let e4 = n2.get(c2);
            void 0 === e4 && (e4 = i2.length, i2.push(c2), n2.set(c2, e4)), f2[4] = e4;
          }
          a2.push(f2);
        }
      }
      r2.push(a2);
    }
    return { mappings: r2, names: i2, sources: e3, sourcesContent: s2 };
  }
  traceSegment(e3, t2, s2) {
    const i2 = this.mappings[e3];
    if (!i2)
      return null;
    let n2 = 0, r2 = i2.length - 1;
    for (; n2 <= r2; ) {
      const e4 = n2 + r2 >> 1, o2 = i2[e4];
      if (o2[0] === t2 || n2 === r2) {
        if (1 == o2.length)
          return null;
        const e5 = this.sources[o2[1]];
        return e5 ? e5.traceSegment(o2[2], o2[3], 5 === o2.length ? this.names[o2[4]] : s2) : null;
      }
      o2[0] > t2 ? r2 = e4 - 1 : n2 = e4 + 1;
    }
    return null;
  }
}
function Do(e3) {
  return function(t2, s2) {
    return s2.mappings ? new Oo(s2, [t2]) : (e3((i2 = s2.plugin, { code: Be, message: `Sourcemap is likely to be incorrect: a plugin (${i2}) was used to transform files, but didn't generate a sourcemap for the transformation. Consult the plugin documentation for help`, plugin: i2, url: Ee("troubleshooting/#warning-sourcemap-is-likely-to-be-incorrect") })), new Oo({ mappings: [], names: [] }, [t2]));
    var i2;
  };
}
function Lo(e3, t2, s2, i2, n2) {
  let r2;
  if (s2) {
    const t3 = s2.sources, i3 = s2.sourcesContent || [], n3 = I(e3) || ".", o2 = s2.sourceRoot || ".", a2 = t3.map((e4, t4) => new Mo($(n3, o2, e4), i3[t4]));
    r2 = new Oo(s2, a2);
  } else
    r2 = new Mo(e3, t2);
  return i2.reduce(n2, r2);
}
var Vo = {}, Bo = Fo;
function Fo(e3, t2) {
  if (!e3)
    throw new Error(t2 || "Assertion failed");
}
Fo.equal = function(e3, t2, s2) {
  if (e3 != t2)
    throw new Error(s2 || "Assertion failed: " + e3 + " != " + t2);
};
var zo = {}, jo = { get exports() {
  return zo;
}, set exports(e3) {
  zo = e3;
} };
"function" == typeof Object.create ? jo.exports = function(e3, t2) {
  t2 && (e3.super_ = t2, e3.prototype = Object.create(t2.prototype, { constructor: { value: e3, enumerable: false, writable: true, configurable: true } }));
} : jo.exports = function(e3, t2) {
  if (t2) {
    e3.super_ = t2;
    var s2 = function() {
    };
    s2.prototype = t2.prototype, e3.prototype = new s2(), e3.prototype.constructor = e3;
  }
};
var Uo = Bo, Go = zo;
function Wo(e3, t2) {
  return 55296 == (64512 & e3.charCodeAt(t2)) && (!(t2 < 0 || t2 + 1 >= e3.length) && 56320 == (64512 & e3.charCodeAt(t2 + 1)));
}
function qo(e3) {
  return (e3 >>> 24 | e3 >>> 8 & 65280 | e3 << 8 & 16711680 | (255 & e3) << 24) >>> 0;
}
function Ho(e3) {
  return 1 === e3.length ? "0" + e3 : e3;
}
function Ko(e3) {
  return 7 === e3.length ? "0" + e3 : 6 === e3.length ? "00" + e3 : 5 === e3.length ? "000" + e3 : 4 === e3.length ? "0000" + e3 : 3 === e3.length ? "00000" + e3 : 2 === e3.length ? "000000" + e3 : 1 === e3.length ? "0000000" + e3 : e3;
}
Vo.inherits = Go, Vo.toArray = function(e3, t2) {
  if (Array.isArray(e3))
    return e3.slice();
  if (!e3)
    return [];
  var s2 = [];
  if ("string" == typeof e3)
    if (t2) {
      if ("hex" === t2)
        for ((e3 = e3.replace(/[^a-z0-9]+/gi, "")).length % 2 != 0 && (e3 = "0" + e3), n2 = 0; n2 < e3.length; n2 += 2)
          s2.push(parseInt(e3[n2] + e3[n2 + 1], 16));
    } else
      for (var i2 = 0, n2 = 0; n2 < e3.length; n2++) {
        var r2 = e3.charCodeAt(n2);
        r2 < 128 ? s2[i2++] = r2 : r2 < 2048 ? (s2[i2++] = r2 >> 6 | 192, s2[i2++] = 63 & r2 | 128) : Wo(e3, n2) ? (r2 = 65536 + ((1023 & r2) << 10) + (1023 & e3.charCodeAt(++n2)), s2[i2++] = r2 >> 18 | 240, s2[i2++] = r2 >> 12 & 63 | 128, s2[i2++] = r2 >> 6 & 63 | 128, s2[i2++] = 63 & r2 | 128) : (s2[i2++] = r2 >> 12 | 224, s2[i2++] = r2 >> 6 & 63 | 128, s2[i2++] = 63 & r2 | 128);
      }
  else
    for (n2 = 0; n2 < e3.length; n2++)
      s2[n2] = 0 | e3[n2];
  return s2;
}, Vo.toHex = function(e3) {
  for (var t2 = "", s2 = 0; s2 < e3.length; s2++)
    t2 += Ho(e3[s2].toString(16));
  return t2;
}, Vo.htonl = qo, Vo.toHex32 = function(e3, t2) {
  for (var s2 = "", i2 = 0; i2 < e3.length; i2++) {
    var n2 = e3[i2];
    "little" === t2 && (n2 = qo(n2)), s2 += Ko(n2.toString(16));
  }
  return s2;
}, Vo.zero2 = Ho, Vo.zero8 = Ko, Vo.join32 = function(e3, t2, s2, i2) {
  var n2 = s2 - t2;
  Uo(n2 % 4 == 0);
  for (var r2 = new Array(n2 / 4), o2 = 0, a2 = t2; o2 < r2.length; o2++, a2 += 4) {
    var l2;
    l2 = "big" === i2 ? e3[a2] << 24 | e3[a2 + 1] << 16 | e3[a2 + 2] << 8 | e3[a2 + 3] : e3[a2 + 3] << 24 | e3[a2 + 2] << 16 | e3[a2 + 1] << 8 | e3[a2], r2[o2] = l2 >>> 0;
  }
  return r2;
}, Vo.split32 = function(e3, t2) {
  for (var s2 = new Array(4 * e3.length), i2 = 0, n2 = 0; i2 < e3.length; i2++, n2 += 4) {
    var r2 = e3[i2];
    "big" === t2 ? (s2[n2] = r2 >>> 24, s2[n2 + 1] = r2 >>> 16 & 255, s2[n2 + 2] = r2 >>> 8 & 255, s2[n2 + 3] = 255 & r2) : (s2[n2 + 3] = r2 >>> 24, s2[n2 + 2] = r2 >>> 16 & 255, s2[n2 + 1] = r2 >>> 8 & 255, s2[n2] = 255 & r2);
  }
  return s2;
}, Vo.rotr32 = function(e3, t2) {
  return e3 >>> t2 | e3 << 32 - t2;
}, Vo.rotl32 = function(e3, t2) {
  return e3 << t2 | e3 >>> 32 - t2;
}, Vo.sum32 = function(e3, t2) {
  return e3 + t2 >>> 0;
}, Vo.sum32_3 = function(e3, t2, s2) {
  return e3 + t2 + s2 >>> 0;
}, Vo.sum32_4 = function(e3, t2, s2, i2) {
  return e3 + t2 + s2 + i2 >>> 0;
}, Vo.sum32_5 = function(e3, t2, s2, i2, n2) {
  return e3 + t2 + s2 + i2 + n2 >>> 0;
}, Vo.sum64 = function(e3, t2, s2, i2) {
  var n2 = e3[t2], r2 = i2 + e3[t2 + 1] >>> 0, o2 = (r2 < i2 ? 1 : 0) + s2 + n2;
  e3[t2] = o2 >>> 0, e3[t2 + 1] = r2;
}, Vo.sum64_hi = function(e3, t2, s2, i2) {
  return (t2 + i2 >>> 0 < t2 ? 1 : 0) + e3 + s2 >>> 0;
}, Vo.sum64_lo = function(e3, t2, s2, i2) {
  return t2 + i2 >>> 0;
}, Vo.sum64_4_hi = function(e3, t2, s2, i2, n2, r2, o2, a2) {
  var l2 = 0, h2 = t2;
  return l2 += (h2 = h2 + i2 >>> 0) < t2 ? 1 : 0, l2 += (h2 = h2 + r2 >>> 0) < r2 ? 1 : 0, e3 + s2 + n2 + o2 + (l2 += (h2 = h2 + a2 >>> 0) < a2 ? 1 : 0) >>> 0;
}, Vo.sum64_4_lo = function(e3, t2, s2, i2, n2, r2, o2, a2) {
  return t2 + i2 + r2 + a2 >>> 0;
}, Vo.sum64_5_hi = function(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2) {
  var c2 = 0, u2 = t2;
  return c2 += (u2 = u2 + i2 >>> 0) < t2 ? 1 : 0, c2 += (u2 = u2 + r2 >>> 0) < r2 ? 1 : 0, c2 += (u2 = u2 + a2 >>> 0) < a2 ? 1 : 0, e3 + s2 + n2 + o2 + l2 + (c2 += (u2 = u2 + h2 >>> 0) < h2 ? 1 : 0) >>> 0;
}, Vo.sum64_5_lo = function(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2) {
  return t2 + i2 + r2 + a2 + h2 >>> 0;
}, Vo.rotr64_hi = function(e3, t2, s2) {
  return (t2 << 32 - s2 | e3 >>> s2) >>> 0;
}, Vo.rotr64_lo = function(e3, t2, s2) {
  return (e3 << 32 - s2 | t2 >>> s2) >>> 0;
}, Vo.shr64_hi = function(e3, t2, s2) {
  return e3 >>> s2;
}, Vo.shr64_lo = function(e3, t2, s2) {
  return (e3 << 32 - s2 | t2 >>> s2) >>> 0;
};
var Yo = {}, Xo = Vo, Qo = Bo;
function Jo() {
  this.pending = null, this.pendingTotal = 0, this.blockSize = this.constructor.blockSize, this.outSize = this.constructor.outSize, this.hmacStrength = this.constructor.hmacStrength, this.padLength = this.constructor.padLength / 8, this.endian = "big", this._delta8 = this.blockSize / 8, this._delta32 = this.blockSize / 32;
}
Yo.BlockHash = Jo, Jo.prototype.update = function(e3, t2) {
  if (e3 = Xo.toArray(e3, t2), this.pending ? this.pending = this.pending.concat(e3) : this.pending = e3, this.pendingTotal += e3.length, this.pending.length >= this._delta8) {
    var s2 = (e3 = this.pending).length % this._delta8;
    this.pending = e3.slice(e3.length - s2, e3.length), 0 === this.pending.length && (this.pending = null), e3 = Xo.join32(e3, 0, e3.length - s2, this.endian);
    for (var i2 = 0; i2 < e3.length; i2 += this._delta32)
      this._update(e3, i2, i2 + this._delta32);
  }
  return this;
}, Jo.prototype.digest = function(e3) {
  return this.update(this._pad()), Qo(null === this.pending), this._digest(e3);
}, Jo.prototype._pad = function() {
  var e3 = this.pendingTotal, t2 = this._delta8, s2 = t2 - (e3 + this.padLength) % t2, i2 = new Array(s2 + this.padLength);
  i2[0] = 128;
  for (var n2 = 1; n2 < s2; n2++)
    i2[n2] = 0;
  if (e3 <<= 3, "big" === this.endian) {
    for (var r2 = 8; r2 < this.padLength; r2++)
      i2[n2++] = 0;
    i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = e3 >>> 24 & 255, i2[n2++] = e3 >>> 16 & 255, i2[n2++] = e3 >>> 8 & 255, i2[n2++] = 255 & e3;
  } else
    for (i2[n2++] = 255 & e3, i2[n2++] = e3 >>> 8 & 255, i2[n2++] = e3 >>> 16 & 255, i2[n2++] = e3 >>> 24 & 255, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, r2 = 8; r2 < this.padLength; r2++)
      i2[n2++] = 0;
  return i2;
};
var Zo = {}, ea = Vo.rotr32;
function ta(e3, t2, s2) {
  return e3 & t2 ^ ~e3 & s2;
}
function sa(e3, t2, s2) {
  return e3 & t2 ^ e3 & s2 ^ t2 & s2;
}
function ia(e3, t2, s2) {
  return e3 ^ t2 ^ s2;
}
Zo.ft_1 = function(e3, t2, s2, i2) {
  return 0 === e3 ? ta(t2, s2, i2) : 1 === e3 || 3 === e3 ? ia(t2, s2, i2) : 2 === e3 ? sa(t2, s2, i2) : void 0;
}, Zo.ch32 = ta, Zo.maj32 = sa, Zo.p32 = ia, Zo.s0_256 = function(e3) {
  return ea(e3, 2) ^ ea(e3, 13) ^ ea(e3, 22);
}, Zo.s1_256 = function(e3) {
  return ea(e3, 6) ^ ea(e3, 11) ^ ea(e3, 25);
}, Zo.g0_256 = function(e3) {
  return ea(e3, 7) ^ ea(e3, 18) ^ e3 >>> 3;
}, Zo.g1_256 = function(e3) {
  return ea(e3, 17) ^ ea(e3, 19) ^ e3 >>> 10;
};
var na = Vo, ra = Yo, oa = Zo, aa = Bo, la = na.sum32, ha = na.sum32_4, ca = na.sum32_5, ua = oa.ch32, da = oa.maj32, pa = oa.s0_256, fa = oa.s1_256, ma = oa.g0_256, ga = oa.g1_256, ya = ra.BlockHash, xa = [1116352408, 1899447441, 3049323471, 3921009573, 961987163, 1508970993, 2453635748, 2870763221, 3624381080, 310598401, 607225278, 1426881987, 1925078388, 2162078206, 2614888103, 3248222580, 3835390401, 4022224774, 264347078, 604807628, 770255983, 1249150122, 1555081692, 1996064986, 2554220882, 2821834349, 2952996808, 3210313671, 3336571891, 3584528711, 113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291, 1695183700, 1986661051, 2177026350, 2456956037, 2730485921, 2820302411, 3259730800, 3345764771, 3516065817, 3600352804, 4094571909, 275423344, 430227734, 506948616, 659060556, 883997877, 958139571, 1322822218, 1537002063, 1747873779, 1955562222, 2024104815, 2227730452, 2361852424, 2428436474, 2756734187, 3204031479, 3329325298];
function ba() {
  if (!(this instanceof ba))
    return new ba();
  ya.call(this), this.h = [1779033703, 3144134277, 1013904242, 2773480762, 1359893119, 2600822924, 528734635, 1541459225], this.k = xa, this.W = new Array(64);
}
na.inherits(ba, ya);
var Ea = ba;
ba.blockSize = 512, ba.outSize = 256, ba.hmacStrength = 192, ba.padLength = 64, ba.prototype._update = function(e3, t2) {
  for (var s2 = this.W, i2 = 0; i2 < 16; i2++)
    s2[i2] = e3[t2 + i2];
  for (; i2 < s2.length; i2++)
    s2[i2] = ha(ga(s2[i2 - 2]), s2[i2 - 7], ma(s2[i2 - 15]), s2[i2 - 16]);
  var n2 = this.h[0], r2 = this.h[1], o2 = this.h[2], a2 = this.h[3], l2 = this.h[4], h2 = this.h[5], c2 = this.h[6], u2 = this.h[7];
  for (aa(this.k.length === s2.length), i2 = 0; i2 < s2.length; i2++) {
    var d2 = ca(u2, fa(l2), ua(l2, h2, c2), this.k[i2], s2[i2]), p2 = la(pa(n2), da(n2, r2, o2));
    u2 = c2, c2 = h2, h2 = l2, l2 = la(a2, d2), a2 = o2, o2 = r2, r2 = n2, n2 = la(d2, p2);
  }
  this.h[0] = la(this.h[0], n2), this.h[1] = la(this.h[1], r2), this.h[2] = la(this.h[2], o2), this.h[3] = la(this.h[3], a2), this.h[4] = la(this.h[4], l2), this.h[5] = la(this.h[5], h2), this.h[6] = la(this.h[6], c2), this.h[7] = la(this.h[7], u2);
}, ba.prototype._digest = function(e3) {
  return "hex" === e3 ? na.toHex32(this.h, "big") : na.split32(this.h, "big");
};
var va = Ea;
const Sa = () => va();
function ka(e3) {
  if (!e3)
    return null;
  if ("string" == typeof e3 && (e3 = JSON.parse(e3)), "" === e3.mappings)
    return { mappings: [], names: [], sources: [], version: 3 };
  const t2 = "string" == typeof e3.mappings ? s.decode(e3.mappings) : e3.mappings;
  return { ...e3, mappings: t2 };
}
async function Aa(e3, t2, s2, i2, n2) {
  ur("render chunks", 2), function(e4) {
    for (const t3 of e4)
      t3.facadeModule && t3.facadeModule.isUserDefinedEntryPoint && t3.getPreliminaryFileName();
  }(e3);
  const r2 = await Promise.all(e3.map((e4) => e4.render()));
  dr("render chunks", 2), ur("transform chunks", 2);
  const o2 = function(e4) {
    return Object.fromEntries(e4.map((e5) => {
      const t3 = e5.getRenderedChunkInfo();
      return [t3.fileName, t3];
    }));
  }(e3), { nonHashedChunksWithPlaceholders: a2, renderedChunksByPlaceholder: l2, hashDependenciesByPlaceholder: h2 } = await async function(e4, t3, s3, i3, n3) {
    const r3 = [], o3 = /* @__PURE__ */ new Map(), a3 = /* @__PURE__ */ new Map(), l3 = /* @__PURE__ */ new Set();
    for (const { preliminaryFileName: { hashPlaceholder: t4 } } of e4)
      t4 && l3.add(t4);
    return await Promise.all(e4.map(async ({ chunk: e5, preliminaryFileName: { fileName: h3, hashPlaceholder: c3 }, magicString: u2, usedModules: d2 }) => {
      const p2 = { chunk: e5, fileName: h3, ...await wa(u2, h3, d2, t3, s3, i3, n3) }, { code: f2 } = p2;
      if (c3) {
        const { containedPlaceholders: t4, transformedCode: s4 } = ((e6, t5) => {
          const s5 = /* @__PURE__ */ new Set(), i4 = e6.replace(no, (e7) => t5.has(e7) ? (s5.add(e7), `${to}${"0".repeat(e7.length - io)}${so}`) : e7);
          return { containedPlaceholders: s5, transformedCode: i4 };
        })(f2, l3), n4 = Sa().update(s4), r4 = i3.hookReduceValueSync("augmentChunkHash", "", [e5.getRenderedChunkInfo()], (e6, t5) => (t5 && (e6 += t5), e6));
        r4 && n4.update(r4), o3.set(c3, p2), a3.set(c3, { containedPlaceholders: t4, contentHash: n4.digest("hex") });
      } else
        r3.push(p2);
    })), { hashDependenciesByPlaceholder: a3, nonHashedChunksWithPlaceholders: r3, renderedChunksByPlaceholder: o3 };
  }(r2, o2, i2, s2, n2), c2 = function(e4, t3, s3) {
    const i3 = /* @__PURE__ */ new Map();
    for (const [n3, { fileName: r3 }] of e4) {
      let e5 = Sa();
      const o3 = /* @__PURE__ */ new Set([n3]);
      for (const s4 of o3) {
        const { containedPlaceholders: i4, contentHash: n4 } = t3.get(s4);
        e5.update(n4);
        for (const e6 of i4)
          o3.add(e6);
      }
      let a3, l3;
      do {
        l3 && (e5 = Sa().update(l3)), l3 = e5.digest("hex").slice(0, n3.length), a3 = oo(r3, n3, l3);
      } while (s3[ao].has(a3.toLowerCase()));
      s3[a3] = lo, i3.set(n3, l3);
    }
    return i3;
  }(l2, h2, t2);
  !function(e4, t3, s3, i3, n3, r3) {
    for (const { chunk: i4, code: o3, fileName: a3, map: l3 } of e4.values()) {
      let e5 = ro(o3, t3);
      const h3 = ro(a3, t3);
      l3 && (l3.file = ro(l3.file, t3), e5 += Ia(h3, l3, n3, r3)), s3[h3] = i4.finalizeChunk(e5, l3, t3);
    }
    for (const { chunk: e5, code: o3, fileName: a3, map: l3 } of i3) {
      let i4 = t3.size > 0 ? ro(o3, t3) : o3;
      l3 && (i4 += Ia(a3, l3, n3, r3)), s3[a3] = e5.finalizeChunk(i4, l3, t3);
    }
  }(l2, c2, t2, a2, s2, i2), dr("transform chunks", 2);
}
async function wa(e3, t2, s2, i2, n2, r2, o2) {
  let l2 = null;
  const h2 = [];
  let c2 = await r2.hookReduceArg0("renderChunk", [e3.toString(), i2[t2], n2, { chunks: i2 }], (e4, t3, s3) => {
    if (null == t3)
      return e4;
    if ("string" == typeof t3 && (t3 = { code: t3, map: void 0 }), null !== t3.map) {
      const e5 = ka(t3.map);
      h2.push(e5 || { missing: true, plugin: s3.name });
    }
    return t3.code;
  });
  const { compact: u2, dir: d2, file: p2, sourcemap: f2, sourcemapExcludeSources: m2, sourcemapFile: g2, sourcemapPathTransform: y2, sourcemapIgnoreList: x2 } = n2;
  if (u2 || "\n" === c2[c2.length - 1] || (c2 += "\n"), f2) {
    let i3;
    ur("sourcemaps", 3), i3 = p2 ? $(g2 || p2) : d2 ? $(d2, t2) : $(t2);
    l2 = function(e4, t3, s3, i4, n3, r3) {
      const o3 = Do(r3), l3 = s3.filter((e5) => !e5.excludeFromSourcemap).map((e5) => Lo(e5.id, e5.originalCode, e5.originalSourcemap, e5.sourcemapChain, o3)), h3 = new Oo(t3, l3), c3 = i4.reduce(o3, h3);
      let { sources: u3, sourcesContent: d3, names: p3, mappings: f3 } = c3.traceMappings();
      if (e4) {
        const t4 = I(e4);
        u3 = u3.map((e5) => C(t4, e5)), e4 = w(e4);
      }
      return d3 = n3 ? null : d3, new a({ file: e4, mappings: f3, names: p3, sources: u3, sourcesContent: d3 });
    }(i3, e3.generateDecodedMap({}), s2, h2, m2, o2);
    for (let e4 = 0; e4 < l2.sources.length; ++e4) {
      let t3 = l2.sources[e4];
      const s3 = `${i3}.map`, n3 = x2(t3, s3);
      "boolean" != typeof n3 && _e(it("sourcemapIgnoreList function must return a boolean.")), n3 && (void 0 === l2.x_google_ignoreList && (l2.x_google_ignoreList = []), l2.x_google_ignoreList.includes(e4) || l2.x_google_ignoreList.push(e4)), y2 && (t3 = y2(t3, s3), "string" != typeof t3 && _e(it("sourcemapPathTransform function must return a string."))), l2.sources[e4] = A(t3);
    }
    dr("sourcemaps", 3);
  }
  return { code: c2, map: l2 };
}
function Ia(e3, t2, s2, { sourcemap: i2, sourcemapBaseUrl: n2 }) {
  let r2;
  if ("inline" === i2)
    r2 = t2.toUrl();
  else {
    const i3 = `${w(e3)}.map`;
    r2 = n2 ? new URL(i3, n2).toString() : i3, s2.emitFile({ fileName: `${e3}.map`, source: t2.toString(), type: "asset" });
  }
  return "hidden" === i2 ? "" : `//# ${zt}=${r2}
`;
}
class Pa {
  constructor(e3, t2, s2, i2, n2) {
    this.outputOptions = e3, this.unsetOptions = t2, this.inputOptions = s2, this.pluginDriver = i2, this.graph = n2, this.facadeChunkByModule = /* @__PURE__ */ new Map(), this.includedNamespaces = /* @__PURE__ */ new Set();
  }
  async generate(e3) {
    ur("GENERATE", 1);
    const t2 = /* @__PURE__ */ Object.create(null), s2 = ((e4) => {
      const t3 = /* @__PURE__ */ new Set();
      return new Proxy(e4, { deleteProperty: (e5, s3) => ("string" == typeof s3 && t3.delete(s3.toLowerCase()), Reflect.deleteProperty(e5, s3)), get: (e5, s3) => s3 === ao ? t3 : Reflect.get(e5, s3), set: (e5, s3, i2) => ("string" == typeof s3 && t3.add(s3.toLowerCase()), Reflect.set(e5, s3, i2)) });
    })(t2);
    this.pluginDriver.setOutputBundle(s2, this.outputOptions);
    try {
      ur("initialize render", 2), await this.pluginDriver.hookParallel("renderStart", [this.outputOptions, this.inputOptions]), dr("initialize render", 2), ur("generate chunks", 2);
      const e4 = (() => {
        let e5 = 0;
        return (t4, s3 = 8) => {
          if (s3 > 64)
            return _e(it(`Hashes cannot be longer than 64 characters, received ${s3}. Check the "${t4}" option.`));
          const i2 = `${to}${Ps(++e5).padStart(s3 - io, "0")}${so}`;
          return i2.length > s3 ? _e(it(`To generate hashes for this number of chunks (currently ${e5}), you need a minimum hash size of ${i2.length}, received ${s3}. Check the "${t4}" option.`)) : i2;
        };
      })(), t3 = await this.generateChunks(s2, e4);
      t3.length > 1 && function(e5, t4) {
        if ("umd" === e5.format || "iife" === e5.format)
          return _e(Xe("output.format", Ae, "UMD and IIFE output formats are not supported for code-splitting builds", e5.format));
        if ("string" == typeof e5.file)
          return _e(Xe("output.file", Se, 'when building multiple chunks, the "output.dir" option must be used, not "output.file". To inline dynamic imports, set the "inlineDynamicImports" option'));
        if (e5.sourcemapFile)
          return _e(Xe("output.sourcemapFile", $e, '"output.sourcemapFile" is only supported for single-file builds'));
        !e5.amd.autoId && e5.amd.id && t4(Xe("output.amd.id", ve, 'this option is only properly supported for single-file builds. Use "output.amd.autoId" and "output.amd.basePath" instead'));
      }(this.outputOptions, this.inputOptions.onwarn), this.pluginDriver.setChunkInformation(this.facadeChunkByModule);
      for (const e5 of t3)
        e5.generateExports();
      dr("generate chunks", 2), await Aa(t3, s2, this.pluginDriver, this.outputOptions, this.inputOptions.onwarn);
    } catch (e4) {
      throw await this.pluginDriver.hookParallel("renderError", [e4]), e4;
    }
    return ((e4) => {
      const t3 = /* @__PURE__ */ new Set(), s3 = Object.values(e4);
      for (const e5 of s3)
        "asset" === e5.type && e5.needsCodeReference && t3.add(e5.fileName);
      for (const e5 of s3)
        if ("chunk" === e5.type)
          for (const s4 of e5.referencedFiles)
            t3.has(s4) && t3.delete(s4);
      for (const s4 of t3)
        delete e4[s4];
    })(s2), ur("generate bundle", 2), await this.pluginDriver.hookSeq("generateBundle", [this.outputOptions, s2, e3]), this.finaliseAssets(s2), dr("generate bundle", 2), dr("GENERATE", 1), t2;
  }
  async addManualChunks(e3) {
    const t2 = /* @__PURE__ */ new Map(), s2 = await Promise.all(Object.entries(e3).map(async ([e4, t3]) => ({ alias: e4, entries: await this.graph.moduleLoader.addAdditionalModules(t3) })));
    for (const { alias: e4, entries: i2 } of s2)
      for (const s3 of i2)
        Ca(e4, s3, t2);
    return t2;
  }
  assignManualChunks(e3) {
    const t2 = [], s2 = { getModuleIds: () => this.graph.modulesById.keys(), getModuleInfo: this.graph.getModuleInfo };
    for (const i3 of this.graph.modulesById.values())
      if (i3 instanceof xr) {
        const n2 = e3(i3.id, s2);
        "string" == typeof n2 && t2.push([n2, i3]);
      }
    t2.sort(([e4], [t3]) => e4 > t3 ? 1 : e4 < t3 ? -1 : 0);
    const i2 = /* @__PURE__ */ new Map();
    for (const [e4, s3] of t2)
      Ca(e4, s3, i2);
    return i2;
  }
  finaliseAssets(e3) {
    if (this.outputOptions.validate) {
      for (const t2 of Object.values(e3))
        if ("code" in t2)
          try {
            this.graph.contextParse(t2.code, { ecmaVersion: "latest" });
          } catch (e4) {
            this.inputOptions.onwarn(Ge(t2, e4));
          }
    }
    this.pluginDriver.finaliseAssets();
  }
  async generateChunks(e3, t2) {
    const { experimentalMinChunkSize: s2, inlineDynamicImports: i2, manualChunks: n2, preserveModules: r2 } = this.outputOptions, o2 = "object" == typeof n2 ? await this.addManualChunks(n2) : this.assignManualChunks(n2), a2 = function({ compact: e4, generatedCode: { arrowFunctions: t3, constBindings: s3, objectShorthand: i3, reservedNamesAsProps: n3 } }) {
      const { _: r3, n: o3, s: a3 } = e4 ? { _: "", n: "", s: "" } : { _: " ", n: "\n", s: ";" }, l3 = s3 ? "const" : "var", h3 = (e5, { isAsync: t4, name: s4 }) => `${t4 ? "async " : ""}function${s4 ? ` ${s4}` : ""}${r3}(${e5.join(`,${r3}`)})${r3}`, c3 = t3 ? (e5, { isAsync: t4, name: s4 }) => {
        const i4 = 1 === e5.length;
        return `${s4 ? `${l3} ${s4}${r3}=${r3}` : ""}${t4 ? `async${i4 ? " " : r3}` : ""}${i4 ? e5[0] : `(${e5.join(`,${r3}`)})`}${r3}=>${r3}`;
      } : h3, u3 = (e5, { functionReturn: s4, lineBreakIndent: i4, name: n4 }) => [`${c3(e5, { isAsync: false, name: n4 })}${t3 ? i4 ? `${o3}${i4.base}${i4.t}` : "" : `{${i4 ? `${o3}${i4.base}${i4.t}` : r3}${s4 ? "return " : ""}`}`, t3 ? `${n4 ? ";" : ""}${i4 ? `${o3}${i4.base}` : ""}` : `${a3}${i4 ? `${o3}${i4.base}` : r3}}`], d3 = n3 ? (e5) => Ro.test(e5) : (e5) => !ot.has(e5) && Ro.test(e5);
      return { _: r3, cnst: l3, getDirectReturnFunction: u3, getDirectReturnIifeLeft: (e5, s4, { needsArrowReturnParens: i4, needsWrappedFunction: n4 }) => {
        const [r4, o4] = u3(e5, { functionReturn: true, lineBreakIndent: null, name: null });
        return `${To(`${r4}${To(s4, t3 && i4)}${o4}`, t3 || n4)}(`;
      }, getFunctionIntro: c3, getNonArrowFunctionIntro: h3, getObject(e5, { lineBreakIndent: t4 }) {
        const s4 = t4 ? `${o3}${t4.base}${t4.t}` : r3;
        return `{${e5.map(([e6, t5]) => {
          if (null === e6)
            return `${s4}${t5}`;
          const n4 = !d3(e6);
          return e6 === t5 && i3 && !n4 ? s4 + e6 : `${s4}${n4 ? `'${e6}'` : e6}:${r3}${t5}`;
        }).join(",")}${0 === e5.length ? "" : t4 ? `${o3}${t4.base}` : r3}}`;
      }, getPropertyAccess: (e5) => d3(e5) ? `.${e5}` : `[${JSON.stringify(e5)}]`, n: o3, s: a3 };
    }(this.outputOptions), l2 = function(e4) {
      const t3 = [];
      for (const s3 of e4.values())
        s3 instanceof xr && (s3.isIncluded() || s3.info.isEntry || s3.includedDynamicImporters.length > 0) && t3.push(s3);
      return t3;
    }(this.graph.modulesById), h2 = function(e4) {
      if (0 === e4.length)
        return "/";
      if (1 === e4.length)
        return I(e4[0]);
      const t3 = e4.slice(1).reduce((e5, t4) => {
        const s3 = t4.split(/\/+|\\+/);
        let i3;
        for (i3 = 0; e5[i3] === s3[i3] && i3 < Math.min(e5.length, s3.length); i3++)
          ;
        return e5.slice(0, i3);
      }, e4[0].split(/\/+|\\+/));
      return t3.length > 1 ? t3.join("/") : "/";
    }(function(e4, t3) {
      const s3 = [];
      for (const i3 of e4)
        (i3.info.isEntry || t3) && S(i3.id) && s3.push(i3.id);
      return s3;
    }(l2, r2)), c2 = function(e4, t3, s3) {
      const i3 = /* @__PURE__ */ new Map();
      for (const n3 of e4.values())
        n3 instanceof ct && i3.set(n3, new B(n3, t3, s3));
      return i3;
    }(this.graph.modulesById, this.outputOptions, h2), u2 = [], d2 = /* @__PURE__ */ new Map();
    for (const { alias: n3, modules: p3 } of i2 ? [{ alias: null, modules: l2 }] : r2 ? l2.map((e4) => ({ alias: null, modules: [e4] })) : vo(this.graph.entryModules, o2, s2)) {
      p3.sort(No);
      const s3 = new fo(p3, this.inputOptions, this.outputOptions, this.unsetOptions, this.pluginDriver, this.graph.modulesById, d2, c2, this.facadeChunkByModule, this.includedNamespaces, n3, t2, e3, h2, a2);
      u2.push(s3);
    }
    for (const e4 of u2)
      e4.link();
    const p2 = [];
    for (const e4 of u2)
      p2.push(...e4.generateFacades());
    return [...u2, ...p2];
  }
}
function Ca(e3, t2, s2) {
  const i2 = s2.get(t2);
  if ("string" == typeof i2 && i2 !== e3)
    return _e((n2 = t2.id, r2 = e3, o2 = i2, { code: "INVALID_CHUNK", message: `Cannot assign "${O(n2)}" to the "${r2}" chunk as it is already in the "${o2}" chunk.` }));
  var n2, r2, o2;
  s2.set(t2, e3);
}
var $a = [509, 0, 227, 0, 150, 4, 294, 9, 1368, 2, 2, 1, 6, 3, 41, 2, 5, 0, 166, 1, 574, 3, 9, 9, 370, 1, 154, 10, 50, 3, 123, 2, 54, 14, 32, 10, 3, 1, 11, 3, 46, 10, 8, 0, 46, 9, 7, 2, 37, 13, 2, 9, 6, 1, 45, 0, 13, 2, 49, 13, 9, 3, 2, 11, 83, 11, 7, 0, 161, 11, 6, 9, 7, 3, 56, 1, 2, 6, 3, 1, 3, 2, 10, 0, 11, 1, 3, 6, 4, 4, 193, 17, 10, 9, 5, 0, 82, 19, 13, 9, 214, 6, 3, 8, 28, 1, 83, 16, 16, 9, 82, 12, 9, 9, 84, 14, 5, 9, 243, 14, 166, 9, 71, 5, 2, 1, 3, 3, 2, 0, 2, 1, 13, 9, 120, 6, 3, 6, 4, 0, 29, 9, 41, 6, 2, 3, 9, 0, 10, 10, 47, 15, 406, 7, 2, 7, 17, 9, 57, 21, 2, 13, 123, 5, 4, 0, 2, 1, 2, 6, 2, 0, 9, 9, 49, 4, 2, 1, 2, 4, 9, 9, 330, 3, 19306, 9, 87, 9, 39, 4, 60, 6, 26, 9, 1014, 0, 2, 54, 8, 3, 82, 0, 12, 1, 19628, 1, 4706, 45, 3, 22, 543, 4, 4, 5, 9, 7, 3, 6, 31, 3, 149, 2, 1418, 49, 513, 54, 5, 49, 9, 0, 15, 0, 23, 4, 2, 14, 1361, 6, 2, 16, 3, 6, 2, 1, 2, 4, 262, 6, 10, 9, 357, 0, 62, 13, 1495, 6, 110, 6, 6, 9, 4759, 9, 787719, 239], Na = [0, 11, 2, 25, 2, 18, 2, 1, 2, 14, 3, 13, 35, 122, 70, 52, 268, 28, 4, 48, 48, 31, 14, 29, 6, 37, 11, 29, 3, 35, 5, 7, 2, 4, 43, 157, 19, 35, 5, 35, 5, 39, 9, 51, 13, 10, 2, 14, 2, 6, 2, 1, 2, 10, 2, 14, 2, 6, 2, 1, 68, 310, 10, 21, 11, 7, 25, 5, 2, 41, 2, 8, 70, 5, 3, 0, 2, 43, 2, 1, 4, 0, 3, 22, 11, 22, 10, 30, 66, 18, 2, 1, 11, 21, 11, 25, 71, 55, 7, 1, 65, 0, 16, 3, 2, 2, 2, 28, 43, 28, 4, 28, 36, 7, 2, 27, 28, 53, 11, 21, 11, 18, 14, 17, 111, 72, 56, 50, 14, 50, 14, 35, 349, 41, 7, 1, 79, 28, 11, 0, 9, 21, 43, 17, 47, 20, 28, 22, 13, 52, 58, 1, 3, 0, 14, 44, 33, 24, 27, 35, 30, 0, 3, 0, 9, 34, 4, 0, 13, 47, 15, 3, 22, 0, 2, 0, 36, 17, 2, 24, 85, 6, 2, 0, 2, 3, 2, 14, 2, 9, 8, 46, 39, 7, 3, 1, 3, 21, 2, 6, 2, 1, 2, 4, 4, 0, 19, 0, 13, 4, 159, 52, 19, 3, 21, 2, 31, 47, 21, 1, 2, 0, 185, 46, 42, 3, 37, 47, 21, 0, 60, 42, 14, 0, 72, 26, 38, 6, 186, 43, 117, 63, 32, 7, 3, 0, 3, 7, 2, 1, 2, 23, 16, 0, 2, 0, 95, 7, 3, 38, 17, 0, 2, 0, 29, 0, 11, 39, 8, 0, 22, 0, 12, 45, 20, 0, 19, 72, 264, 8, 2, 36, 18, 0, 50, 29, 113, 6, 2, 1, 2, 37, 22, 0, 26, 5, 2, 1, 2, 31, 15, 0, 328, 18, 190, 0, 80, 921, 103, 110, 18, 195, 2637, 96, 16, 1070, 4050, 582, 8634, 568, 8, 30, 18, 78, 18, 29, 19, 47, 17, 3, 32, 20, 6, 18, 689, 63, 129, 74, 6, 0, 67, 12, 65, 1, 2, 0, 29, 6135, 9, 1237, 43, 8, 8936, 3, 2, 6, 2, 1, 2, 290, 46, 2, 18, 3, 9, 395, 2309, 106, 6, 12, 4, 8, 8, 9, 5991, 84, 2, 70, 2, 1, 3, 0, 3, 1, 3, 3, 2, 11, 2, 0, 2, 6, 2, 64, 2, 3, 3, 7, 2, 6, 2, 27, 2, 3, 2, 4, 2, 0, 4, 6, 2, 339, 3, 24, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 7, 1845, 30, 482, 44, 11, 6, 17, 0, 322, 29, 19, 43, 1269, 6, 2, 3, 2, 1, 2, 14, 2, 196, 60, 67, 8, 0, 1205, 3, 2, 26, 2, 1, 2, 0, 3, 0, 2, 9, 2, 3, 2, 0, 2, 0, 7, 0, 5, 0, 2, 0, 2, 0, 2, 2, 2, 1, 2, 0, 3, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 1, 2, 0, 3, 3, 2, 6, 2, 3, 2, 3, 2, 0, 2, 9, 2, 16, 6, 2, 2, 4, 2, 16, 4421, 42719, 33, 4152, 8, 221, 3, 5761, 15, 7472, 3104, 541, 1507, 4938], _a = "ªµºÀ-ÖØ-öø-ˁˆ-ˑˠ-ˤˬˮͰ-ʹͶͷͺ-ͽͿΆΈ-ΊΌΎ-ΡΣ-ϵϷ-ҁҊ-ԯԱ-Ֆՙՠ-ֈא-תׯ-ײؠ-يٮٯٱ-ۓەۥۦۮۯۺ-ۼۿܐܒ-ܯݍ-ޥޱߊ-ߪߴߵߺࠀ-ࠕࠚࠤࠨࡀ-ࡘࡠ-ࡪࡰ-ࢇࢉ-ࢎࢠ-ࣉऄ-हऽॐक़-ॡॱ-ঀঅ-ঌএঐও-নপ-রলশ-হঽৎড়ঢ়য়-ৡৰৱৼਅ-ਊਏਐਓ-ਨਪ-ਰਲਲ਼ਵਸ਼ਸਹਖ਼-ੜਫ਼ੲ-ੴઅ-ઍએ-ઑઓ-નપ-રલળવ-હઽૐૠૡૹଅ-ଌଏଐଓ-ନପ-ରଲଳଵ-ହଽଡ଼ଢ଼ୟ-ୡୱஃஅ-ஊஎ-ஐஒ-கஙசஜஞடணதந-பம-ஹௐఅ-ఌఎ-ఐఒ-నప-హఽౘ-ౚౝౠౡಀಅ-ಌಎ-ಐಒ-ನಪ-ಳವ-ಹಽೝೞೠೡೱೲഄ-ഌഎ-ഐഒ-ഺഽൎൔ-ൖൟ-ൡൺ-ൿඅ-ඖක-නඳ-රලව-ෆก-ะาำเ-ๆກຂຄຆ-ຊຌ-ຣລວ-ະາຳຽເ-ໄໆໜ-ໟༀཀ-ཇཉ-ཬྈ-ྌက-ဪဿၐ-ၕၚ-ၝၡၥၦၮ-ၰၵ-ႁႎႠ-ჅჇჍა-ჺჼ-ቈቊ-ቍቐ-ቖቘቚ-ቝበ-ኈኊ-ኍነ-ኰኲ-ኵኸ-ኾዀዂ-ዅወ-ዖዘ-ጐጒ-ጕጘ-ፚᎀ-ᎏᎠ-Ᏽᏸ-ᏽᐁ-ᙬᙯ-ᙿᚁ-ᚚᚠ-ᛪᛮ-ᛸᜀ-ᜑᜟ-ᜱᝀ-ᝑᝠ-ᝬᝮ-ᝰក-ឳៗៜᠠ-ᡸᢀ-ᢨᢪᢰ-ᣵᤀ-ᤞᥐ-ᥭᥰ-ᥴᦀ-ᦫᦰ-ᧉᨀ-ᨖᨠ-ᩔᪧᬅ-ᬳᭅ-ᭌᮃ-ᮠᮮᮯᮺ-ᯥᰀ-ᰣᱍ-ᱏᱚ-ᱽᲀ-ᲈᲐ-ᲺᲽ-Ჿᳩ-ᳬᳮ-ᳳᳵᳶᳺᴀ-ᶿḀ-ἕἘ-Ἕἠ-ὅὈ-Ὅὐ-ὗὙὛὝὟ-ώᾀ-ᾴᾶ-ᾼιῂ-ῄῆ-ῌῐ-ΐῖ-Ίῠ-Ῥῲ-ῴῶ-ῼⁱⁿₐ-ₜℂℇℊ-ℓℕ℘-ℝℤΩℨK-ℹℼ-ℿⅅ-ⅉⅎⅠ-ↈⰀ-ⳤⳫ-ⳮⳲⳳⴀ-ⴥⴧⴭⴰ-ⵧⵯⶀ-ⶖⶠ-ⶦⶨ-ⶮⶰ-ⶶⶸ-ⶾⷀ-ⷆⷈ-ⷎⷐ-ⷖⷘ-ⷞ々-〇〡-〩〱-〵〸-〼ぁ-ゖ゛-ゟァ-ヺー-ヿㄅ-ㄯㄱ-ㆎㆠ-ㆿㇰ-ㇿ㐀-䶿一-ꒌꓐ-ꓽꔀ-ꘌꘐ-ꘟꘪꘫꙀ-ꙮꙿ-ꚝꚠ-ꛯꜗ-ꜟꜢ-ꞈꞋ-ꟊꟐꟑꟓꟕ-ꟙꟲ-ꠁꠃ-ꠅꠇ-ꠊꠌ-ꠢꡀ-ꡳꢂ-ꢳꣲ-ꣷꣻꣽꣾꤊ-ꤥꤰ-ꥆꥠ-ꥼꦄ-ꦲꧏꧠ-ꧤꧦ-ꧯꧺ-ꧾꨀ-ꨨꩀ-ꩂꩄ-ꩋꩠ-ꩶꩺꩾ-ꪯꪱꪵꪶꪹ-ꪽꫀꫂꫛ-ꫝꫠ-ꫪꫲ-ꫴꬁ-ꬆꬉ-ꬎꬑ-ꬖꬠ-ꬦꬨ-ꬮꬰ-ꭚꭜ-ꭩꭰ-ꯢ가-힣ힰ-ퟆퟋ-ퟻ豈-舘並-龎ﬀ-ﬆﬓ-ﬗיִײַ-ﬨשׁ-זּטּ-לּמּנּסּףּפּצּ-ﮱﯓ-ﴽﵐ-ﶏﶒ-ﷇﷰ-ﷻﹰ-ﹴﹶ-ﻼＡ-Ｚａ-ｚｦ-ﾾￂ-ￇￊ-ￏￒ-ￗￚ-ￜ", Ta = { 3: "abstract boolean byte char class double enum export extends final float goto implements import int interface long native package private protected public short static super synchronized throws transient volatile", 5: "class enum extends super const export import", 6: "enum", strict: "implements interface let package private protected public static yield", strictBind: "eval arguments" }, Ra = "break case catch continue debugger default do else finally for function if return switch throw try var while with null true false instanceof typeof void delete new in this", Ma = { 5: Ra, "5module": Ra + " export import", 6: Ra + " const class extends export import super" }, Oa = /^in(stanceof)?$/, Da = new RegExp("[" + _a + "]"), La = new RegExp("[" + _a + "‌‍·̀-ͯ·҃-֑҇-ׇֽֿׁׂׅׄؐ-ًؚ-٩ٰۖ-ۜ۟-۪ۤۧۨ-ۭ۰-۹ܑܰ-݊ަ-ް߀-߉߫-߽߳ࠖ-࠙ࠛ-ࠣࠥ-ࠧࠩ-࡙࠭-࡛࢘-࢟࣊-ࣣ࣡-ःऺ-़ा-ॏ॑-ॗॢॣ०-९ঁ-ঃ়া-ৄেৈো-্ৗৢৣ০-৯৾ਁ-ਃ਼ਾ-ੂੇੈੋ-੍ੑ੦-ੱੵઁ-ઃ઼ા-ૅે-ૉો-્ૢૣ૦-૯ૺ-૿ଁ-ଃ଼ା-ୄେୈୋ-୍୕-ୗୢୣ୦-୯ஂா-ூெ-ைொ-்ௗ௦-௯ఀ-ఄ఼ా-ౄె-ైొ-్ౕౖౢౣ౦-౯ಁ-ಃ಼ಾ-ೄೆ-ೈೊ-್ೕೖೢೣ೦-೯ഀ-ഃ഻഼ാ-ൄെ-ൈൊ-്ൗൢൣ൦-൯ඁ-ඃ්ා-ුූෘ-ෟ෦-෯ෲෳัิ-ฺ็-๎๐-๙ັິ-ຼ່-ໍ໐-໙༘༙༠-༩༹༵༷༾༿ཱ-྄྆྇ྍ-ྗྙ-ྼ࿆ါ-ှ၀-၉ၖ-ၙၞ-ၠၢ-ၤၧ-ၭၱ-ၴႂ-ႍႏ-ႝ፝-፟፩-፱ᜒ-᜕ᜲ-᜴ᝒᝓᝲᝳ឴-៓៝០-៩᠋-᠍᠏-᠙ᢩᤠ-ᤫᤰ-᤻᥆-᥏᧐-᧚ᨗ-ᨛᩕ-ᩞ᩠-᩿᩼-᪉᪐-᪙᪰-᪽ᪿ-ᫎᬀ-ᬄ᬴-᭄᭐-᭙᭫-᭳ᮀ-ᮂᮡ-ᮭ᮰-᮹᯦-᯳ᰤ-᰷᱀-᱉᱐-᱙᳐-᳔᳒-᳨᳭᳴᳷-᳹᷀-᷿‿⁀⁔⃐-⃥⃜⃡-⃰⳯-⵿⳱ⷠ-〪ⷿ-゙゚〯꘠-꘩꙯ꙴ-꙽ꚞꚟ꛰꛱ꠂ꠆ꠋꠣ-ꠧ꠬ꢀꢁꢴ-ꣅ꣐-꣙꣠-꣱ꣿ-꤉ꤦ-꤭ꥇ-꥓ꦀ-ꦃ꦳-꧀꧐-꧙ꧥ꧰-꧹ꨩ-ꨶꩃꩌꩍ꩐-꩙ꩻ-ꩽꪰꪲ-ꪴꪷꪸꪾ꪿꫁ꫫ-ꫯꫵ꫶ꯣ-ꯪ꯬꯭꯰-꯹ﬞ︀-️︠-︯︳︴﹍-﹏０-９＿]");
function Va(e3, t2) {
  for (var s2 = 65536, i2 = 0; i2 < t2.length; i2 += 2) {
    if ((s2 += t2[i2]) > e3)
      return false;
    if ((s2 += t2[i2 + 1]) >= e3)
      return true;
  }
}
function Ba(e3, t2) {
  return e3 < 65 ? 36 === e3 : e3 < 91 || (e3 < 97 ? 95 === e3 : e3 < 123 || (e3 <= 65535 ? e3 >= 170 && Da.test(String.fromCharCode(e3)) : false !== t2 && Va(e3, Na)));
}
function Fa(e3, t2) {
  return e3 < 48 ? 36 === e3 : e3 < 58 || !(e3 < 65) && (e3 < 91 || (e3 < 97 ? 95 === e3 : e3 < 123 || (e3 <= 65535 ? e3 >= 170 && La.test(String.fromCharCode(e3)) : false !== t2 && (Va(e3, Na) || Va(e3, $a)))));
}
var za = function(e3, t2) {
  void 0 === t2 && (t2 = {}), this.label = e3, this.keyword = t2.keyword, this.beforeExpr = !!t2.beforeExpr, this.startsExpr = !!t2.startsExpr, this.isLoop = !!t2.isLoop, this.isAssign = !!t2.isAssign, this.prefix = !!t2.prefix, this.postfix = !!t2.postfix, this.binop = t2.binop || null, this.updateContext = null;
};
function ja(e3, t2) {
  return new za(e3, { beforeExpr: true, binop: t2 });
}
var Ua = { beforeExpr: true }, Ga = { startsExpr: true }, Wa = {};
function qa(e3, t2) {
  return void 0 === t2 && (t2 = {}), t2.keyword = e3, Wa[e3] = new za(e3, t2);
}
var Ha = { num: new za("num", Ga), regexp: new za("regexp", Ga), string: new za("string", Ga), name: new za("name", Ga), privateId: new za("privateId", Ga), eof: new za("eof"), bracketL: new za("[", { beforeExpr: true, startsExpr: true }), bracketR: new za("]"), braceL: new za("{", { beforeExpr: true, startsExpr: true }), braceR: new za("}"), parenL: new za("(", { beforeExpr: true, startsExpr: true }), parenR: new za(")"), comma: new za(",", Ua), semi: new za(";", Ua), colon: new za(":", Ua), dot: new za("."), question: new za("?", Ua), questionDot: new za("?."), arrow: new za("=>", Ua), template: new za("template"), invalidTemplate: new za("invalidTemplate"), ellipsis: new za("...", Ua), backQuote: new za("`", Ga), dollarBraceL: new za("${", { beforeExpr: true, startsExpr: true }), eq: new za("=", { beforeExpr: true, isAssign: true }), assign: new za("_=", { beforeExpr: true, isAssign: true }), incDec: new za("++/--", { prefix: true, postfix: true, startsExpr: true }), prefix: new za("!/~", { beforeExpr: true, prefix: true, startsExpr: true }), logicalOR: ja("||", 1), logicalAND: ja("&&", 2), bitwiseOR: ja("|", 3), bitwiseXOR: ja("^", 4), bitwiseAND: ja("&", 5), equality: ja("==/!=/===/!==", 6), relational: ja("</>/<=/>=", 7), bitShift: ja("<</>>/>>>", 8), plusMin: new za("+/-", { beforeExpr: true, binop: 9, prefix: true, startsExpr: true }), modulo: ja("%", 10), star: ja("*", 10), slash: ja("/", 10), starstar: new za("**", { beforeExpr: true }), coalesce: ja("??", 1), _break: qa("break"), _case: qa("case", Ua), _catch: qa("catch"), _continue: qa("continue"), _debugger: qa("debugger"), _default: qa("default", Ua), _do: qa("do", { isLoop: true, beforeExpr: true }), _else: qa("else", Ua), _finally: qa("finally"), _for: qa("for", { isLoop: true }), _function: qa("function", Ga), _if: qa("if"), _return: qa("return", Ua), _switch: qa("switch"), _throw: qa("throw", Ua), _try: qa("try"), _var: qa("var"), _const: qa("const"), _while: qa("while", { isLoop: true }), _with: qa("with"), _new: qa("new", { beforeExpr: true, startsExpr: true }), _this: qa("this", Ga), _super: qa("super", Ga), _class: qa("class", Ga), _extends: qa("extends", Ua), _export: qa("export"), _import: qa("import", Ga), _null: qa("null", Ga), _true: qa("true", Ga), _false: qa("false", Ga), _in: qa("in", { beforeExpr: true, binop: 7 }), _instanceof: qa("instanceof", { beforeExpr: true, binop: 7 }), _typeof: qa("typeof", { beforeExpr: true, prefix: true, startsExpr: true }), _void: qa("void", { beforeExpr: true, prefix: true, startsExpr: true }), _delete: qa("delete", { beforeExpr: true, prefix: true, startsExpr: true }) }, Ka = /\r\n?|\n|\u2028|\u2029/, Ya = new RegExp(Ka.source, "g");
function Xa(e3) {
  return 10 === e3 || 13 === e3 || 8232 === e3 || 8233 === e3;
}
function Qa(e3, t2, s2) {
  void 0 === s2 && (s2 = e3.length);
  for (var i2 = t2; i2 < s2; i2++) {
    var n2 = e3.charCodeAt(i2);
    if (Xa(n2))
      return i2 < s2 - 1 && 13 === n2 && 10 === e3.charCodeAt(i2 + 1) ? i2 + 2 : i2 + 1;
  }
  return -1;
}
var Ja = /[\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]/, Za = /(?:\s|\/\/.*|\/\*[^]*?\*\/)*/g, el = Object.prototype, tl = el.hasOwnProperty, sl = el.toString, il = Object.hasOwn || function(e3, t2) {
  return tl.call(e3, t2);
}, nl = Array.isArray || function(e3) {
  return "[object Array]" === sl.call(e3);
};
function rl(e3) {
  return new RegExp("^(?:" + e3.replace(/ /g, "|") + ")$");
}
function ol(e3) {
  return e3 <= 65535 ? String.fromCharCode(e3) : (e3 -= 65536, String.fromCharCode(55296 + (e3 >> 10), 56320 + (1023 & e3)));
}
var al = /(?:[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF])/, ll = function(e3, t2) {
  this.line = e3, this.column = t2;
};
ll.prototype.offset = function(e3) {
  return new ll(this.line, this.column + e3);
};
var hl = function(e3, t2, s2) {
  this.start = t2, this.end = s2, null !== e3.sourceFile && (this.source = e3.sourceFile);
};
function cl(e3, t2) {
  for (var s2 = 1, i2 = 0; ; ) {
    var n2 = Qa(e3, i2, t2);
    if (n2 < 0)
      return new ll(s2, t2 - i2);
    ++s2, i2 = n2;
  }
}
var ul = { ecmaVersion: null, sourceType: "script", onInsertedSemicolon: null, onTrailingComma: null, allowReserved: null, allowReturnOutsideFunction: false, allowImportExportEverywhere: false, allowAwaitOutsideFunction: null, allowSuperOutsideMethod: null, allowHashBang: false, locations: false, onToken: null, onComment: null, ranges: false, program: null, sourceFile: null, directSourceFile: null, preserveParens: false }, dl = false;
function pl(e3) {
  var t2 = {};
  for (var s2 in ul)
    t2[s2] = e3 && il(e3, s2) ? e3[s2] : ul[s2];
  if ("latest" === t2.ecmaVersion ? t2.ecmaVersion = 1e8 : null == t2.ecmaVersion ? (!dl && "object" == typeof console && console.warn && (dl = true, console.warn("Since Acorn 8.0.0, options.ecmaVersion is required.\nDefaulting to 2020, but this will stop working in the future.")), t2.ecmaVersion = 11) : t2.ecmaVersion >= 2015 && (t2.ecmaVersion -= 2009), null == t2.allowReserved && (t2.allowReserved = t2.ecmaVersion < 5), null == e3.allowHashBang && (t2.allowHashBang = t2.ecmaVersion >= 14), nl(t2.onToken)) {
    var i2 = t2.onToken;
    t2.onToken = function(e4) {
      return i2.push(e4);
    };
  }
  return nl(t2.onComment) && (t2.onComment = function(e4, t3) {
    return function(s3, i3, n2, r2, o2, a2) {
      var l2 = { type: s3 ? "Block" : "Line", value: i3, start: n2, end: r2 };
      e4.locations && (l2.loc = new hl(this, o2, a2)), e4.ranges && (l2.range = [n2, r2]), t3.push(l2);
    };
  }(t2, t2.onComment)), t2;
}
var fl = 256;
function ml(e3, t2) {
  return 2 | (e3 ? 4 : 0) | (t2 ? 8 : 0);
}
var gl = function(e3, t2, s2) {
  this.options = e3 = pl(e3), this.sourceFile = e3.sourceFile, this.keywords = rl(Ma[e3.ecmaVersion >= 6 ? 6 : "module" === e3.sourceType ? "5module" : 5]);
  var i2 = "";
  true !== e3.allowReserved && (i2 = Ta[e3.ecmaVersion >= 6 ? 6 : 5 === e3.ecmaVersion ? 5 : 3], "module" === e3.sourceType && (i2 += " await")), this.reservedWords = rl(i2);
  var n2 = (i2 ? i2 + " " : "") + Ta.strict;
  this.reservedWordsStrict = rl(n2), this.reservedWordsStrictBind = rl(n2 + " " + Ta.strictBind), this.input = String(t2), this.containsEsc = false, s2 ? (this.pos = s2, this.lineStart = this.input.lastIndexOf("\n", s2 - 1) + 1, this.curLine = this.input.slice(0, this.lineStart).split(Ka).length) : (this.pos = this.lineStart = 0, this.curLine = 1), this.type = Ha.eof, this.value = null, this.start = this.end = this.pos, this.startLoc = this.endLoc = this.curPosition(), this.lastTokEndLoc = this.lastTokStartLoc = null, this.lastTokStart = this.lastTokEnd = this.pos, this.context = this.initialContext(), this.exprAllowed = true, this.inModule = "module" === e3.sourceType, this.strict = this.inModule || this.strictDirective(this.pos), this.potentialArrowAt = -1, this.potentialArrowInForAwait = false, this.yieldPos = this.awaitPos = this.awaitIdentPos = 0, this.labels = [], this.undefinedExports = /* @__PURE__ */ Object.create(null), 0 === this.pos && e3.allowHashBang && "#!" === this.input.slice(0, 2) && this.skipLineComment(2), this.scopeStack = [], this.enterScope(1), this.regexpState = null, this.privateNameStack = [];
}, yl = { inFunction: { configurable: true }, inGenerator: { configurable: true }, inAsync: { configurable: true }, canAwait: { configurable: true }, allowSuper: { configurable: true }, allowDirectSuper: { configurable: true }, treatFunctionsAsVar: { configurable: true }, allowNewDotTarget: { configurable: true }, inClassStaticBlock: { configurable: true } };
gl.prototype.parse = function() {
  var e3 = this.options.program || this.startNode();
  return this.nextToken(), this.parseTopLevel(e3);
}, yl.inFunction.get = function() {
  return (2 & this.currentVarScope().flags) > 0;
}, yl.inGenerator.get = function() {
  return (8 & this.currentVarScope().flags) > 0 && !this.currentVarScope().inClassFieldInit;
}, yl.inAsync.get = function() {
  return (4 & this.currentVarScope().flags) > 0 && !this.currentVarScope().inClassFieldInit;
}, yl.canAwait.get = function() {
  for (var e3 = this.scopeStack.length - 1; e3 >= 0; e3--) {
    var t2 = this.scopeStack[e3];
    if (t2.inClassFieldInit || t2.flags & fl)
      return false;
    if (2 & t2.flags)
      return (4 & t2.flags) > 0;
  }
  return this.inModule && this.options.ecmaVersion >= 13 || this.options.allowAwaitOutsideFunction;
}, yl.allowSuper.get = function() {
  var e3 = this.currentThisScope(), t2 = e3.flags, s2 = e3.inClassFieldInit;
  return (64 & t2) > 0 || s2 || this.options.allowSuperOutsideMethod;
}, yl.allowDirectSuper.get = function() {
  return (128 & this.currentThisScope().flags) > 0;
}, yl.treatFunctionsAsVar.get = function() {
  return this.treatFunctionsAsVarInScope(this.currentScope());
}, yl.allowNewDotTarget.get = function() {
  var e3 = this.currentThisScope(), t2 = e3.flags, s2 = e3.inClassFieldInit;
  return (258 & t2) > 0 || s2;
}, yl.inClassStaticBlock.get = function() {
  return (this.currentVarScope().flags & fl) > 0;
}, gl.extend = function() {
  for (var e3 = [], t2 = arguments.length; t2--; )
    e3[t2] = arguments[t2];
  for (var s2 = this, i2 = 0; i2 < e3.length; i2++)
    s2 = e3[i2](s2);
  return s2;
}, gl.parse = function(e3, t2) {
  return new this(t2, e3).parse();
}, gl.parseExpressionAt = function(e3, t2, s2) {
  var i2 = new this(s2, e3, t2);
  return i2.nextToken(), i2.parseExpression();
}, gl.tokenizer = function(e3, t2) {
  return new this(t2, e3);
}, Object.defineProperties(gl.prototype, yl);
var xl = gl.prototype, bl = /^(?:'((?:\\.|[^'\\])*?)'|"((?:\\.|[^"\\])*?)")/;
xl.strictDirective = function(e3) {
  if (this.options.ecmaVersion < 5)
    return false;
  for (; ; ) {
    Za.lastIndex = e3, e3 += Za.exec(this.input)[0].length;
    var t2 = bl.exec(this.input.slice(e3));
    if (!t2)
      return false;
    if ("use strict" === (t2[1] || t2[2])) {
      Za.lastIndex = e3 + t2[0].length;
      var s2 = Za.exec(this.input), i2 = s2.index + s2[0].length, n2 = this.input.charAt(i2);
      return ";" === n2 || "}" === n2 || Ka.test(s2[0]) && !(/[(`.[+\-/*%<>=,?^&]/.test(n2) || "!" === n2 && "=" === this.input.charAt(i2 + 1));
    }
    e3 += t2[0].length, Za.lastIndex = e3, e3 += Za.exec(this.input)[0].length, ";" === this.input[e3] && e3++;
  }
}, xl.eat = function(e3) {
  return this.type === e3 && (this.next(), true);
}, xl.isContextual = function(e3) {
  return this.type === Ha.name && this.value === e3 && !this.containsEsc;
}, xl.eatContextual = function(e3) {
  return !!this.isContextual(e3) && (this.next(), true);
}, xl.expectContextual = function(e3) {
  this.eatContextual(e3) || this.unexpected();
}, xl.canInsertSemicolon = function() {
  return this.type === Ha.eof || this.type === Ha.braceR || Ka.test(this.input.slice(this.lastTokEnd, this.start));
}, xl.insertSemicolon = function() {
  if (this.canInsertSemicolon())
    return this.options.onInsertedSemicolon && this.options.onInsertedSemicolon(this.lastTokEnd, this.lastTokEndLoc), true;
}, xl.semicolon = function() {
  this.eat(Ha.semi) || this.insertSemicolon() || this.unexpected();
}, xl.afterTrailingComma = function(e3, t2) {
  if (this.type === e3)
    return this.options.onTrailingComma && this.options.onTrailingComma(this.lastTokStart, this.lastTokStartLoc), t2 || this.next(), true;
}, xl.expect = function(e3) {
  this.eat(e3) || this.unexpected();
}, xl.unexpected = function(e3) {
  this.raise(null != e3 ? e3 : this.start, "Unexpected token");
};
var El = function() {
  this.shorthandAssign = this.trailingComma = this.parenthesizedAssign = this.parenthesizedBind = this.doubleProto = -1;
};
xl.checkPatternErrors = function(e3, t2) {
  if (e3) {
    e3.trailingComma > -1 && this.raiseRecoverable(e3.trailingComma, "Comma is not permitted after the rest element");
    var s2 = t2 ? e3.parenthesizedAssign : e3.parenthesizedBind;
    s2 > -1 && this.raiseRecoverable(s2, t2 ? "Assigning to rvalue" : "Parenthesized pattern");
  }
}, xl.checkExpressionErrors = function(e3, t2) {
  if (!e3)
    return false;
  var s2 = e3.shorthandAssign, i2 = e3.doubleProto;
  if (!t2)
    return s2 >= 0 || i2 >= 0;
  s2 >= 0 && this.raise(s2, "Shorthand property assignments are valid only in destructuring patterns"), i2 >= 0 && this.raiseRecoverable(i2, "Redefinition of __proto__ property");
}, xl.checkYieldAwaitInDefaultParams = function() {
  this.yieldPos && (!this.awaitPos || this.yieldPos < this.awaitPos) && this.raise(this.yieldPos, "Yield expression cannot be a default value"), this.awaitPos && this.raise(this.awaitPos, "Await expression cannot be a default value");
}, xl.isSimpleAssignTarget = function(e3) {
  return "ParenthesizedExpression" === e3.type ? this.isSimpleAssignTarget(e3.expression) : "Identifier" === e3.type || "MemberExpression" === e3.type;
};
var vl = gl.prototype;
vl.parseTopLevel = function(e3) {
  var t2 = /* @__PURE__ */ Object.create(null);
  for (e3.body || (e3.body = []); this.type !== Ha.eof; ) {
    var s2 = this.parseStatement(null, true, t2);
    e3.body.push(s2);
  }
  if (this.inModule)
    for (var i2 = 0, n2 = Object.keys(this.undefinedExports); i2 < n2.length; i2 += 1) {
      var r2 = n2[i2];
      this.raiseRecoverable(this.undefinedExports[r2].start, "Export '" + r2 + "' is not defined");
    }
  return this.adaptDirectivePrologue(e3.body), this.next(), e3.sourceType = this.options.sourceType, this.finishNode(e3, "Program");
};
var Sl = { kind: "loop" }, kl = { kind: "switch" };
vl.isLet = function(e3) {
  if (this.options.ecmaVersion < 6 || !this.isContextual("let"))
    return false;
  Za.lastIndex = this.pos;
  var t2 = Za.exec(this.input), s2 = this.pos + t2[0].length, i2 = this.input.charCodeAt(s2);
  if (91 === i2 || 92 === i2 || i2 > 55295 && i2 < 56320)
    return true;
  if (e3)
    return false;
  if (123 === i2)
    return true;
  if (Ba(i2, true)) {
    for (var n2 = s2 + 1; Fa(i2 = this.input.charCodeAt(n2), true); )
      ++n2;
    if (92 === i2 || i2 > 55295 && i2 < 56320)
      return true;
    var r2 = this.input.slice(s2, n2);
    if (!Oa.test(r2))
      return true;
  }
  return false;
}, vl.isAsyncFunction = function() {
  if (this.options.ecmaVersion < 8 || !this.isContextual("async"))
    return false;
  Za.lastIndex = this.pos;
  var e3, t2 = Za.exec(this.input), s2 = this.pos + t2[0].length;
  return !(Ka.test(this.input.slice(this.pos, s2)) || "function" !== this.input.slice(s2, s2 + 8) || s2 + 8 !== this.input.length && (Fa(e3 = this.input.charCodeAt(s2 + 8)) || e3 > 55295 && e3 < 56320));
}, vl.parseStatement = function(e3, t2, s2) {
  var i2, n2 = this.type, r2 = this.startNode();
  switch (this.isLet(e3) && (n2 = Ha._var, i2 = "let"), n2) {
    case Ha._break:
    case Ha._continue:
      return this.parseBreakContinueStatement(r2, n2.keyword);
    case Ha._debugger:
      return this.parseDebuggerStatement(r2);
    case Ha._do:
      return this.parseDoStatement(r2);
    case Ha._for:
      return this.parseForStatement(r2);
    case Ha._function:
      return e3 && (this.strict || "if" !== e3 && "label" !== e3) && this.options.ecmaVersion >= 6 && this.unexpected(), this.parseFunctionStatement(r2, false, !e3);
    case Ha._class:
      return e3 && this.unexpected(), this.parseClass(r2, true);
    case Ha._if:
      return this.parseIfStatement(r2);
    case Ha._return:
      return this.parseReturnStatement(r2);
    case Ha._switch:
      return this.parseSwitchStatement(r2);
    case Ha._throw:
      return this.parseThrowStatement(r2);
    case Ha._try:
      return this.parseTryStatement(r2);
    case Ha._const:
    case Ha._var:
      return i2 = i2 || this.value, e3 && "var" !== i2 && this.unexpected(), this.parseVarStatement(r2, i2);
    case Ha._while:
      return this.parseWhileStatement(r2);
    case Ha._with:
      return this.parseWithStatement(r2);
    case Ha.braceL:
      return this.parseBlock(true, r2);
    case Ha.semi:
      return this.parseEmptyStatement(r2);
    case Ha._export:
    case Ha._import:
      if (this.options.ecmaVersion > 10 && n2 === Ha._import) {
        Za.lastIndex = this.pos;
        var o2 = Za.exec(this.input), a2 = this.pos + o2[0].length, l2 = this.input.charCodeAt(a2);
        if (40 === l2 || 46 === l2)
          return this.parseExpressionStatement(r2, this.parseExpression());
      }
      return this.options.allowImportExportEverywhere || (t2 || this.raise(this.start, "'import' and 'export' may only appear at the top level"), this.inModule || this.raise(this.start, "'import' and 'export' may appear only with 'sourceType: module'")), n2 === Ha._import ? this.parseImport(r2) : this.parseExport(r2, s2);
    default:
      if (this.isAsyncFunction())
        return e3 && this.unexpected(), this.next(), this.parseFunctionStatement(r2, true, !e3);
      var h2 = this.value, c2 = this.parseExpression();
      return n2 === Ha.name && "Identifier" === c2.type && this.eat(Ha.colon) ? this.parseLabeledStatement(r2, h2, c2, e3) : this.parseExpressionStatement(r2, c2);
  }
}, vl.parseBreakContinueStatement = function(e3, t2) {
  var s2 = "break" === t2;
  this.next(), this.eat(Ha.semi) || this.insertSemicolon() ? e3.label = null : this.type !== Ha.name ? this.unexpected() : (e3.label = this.parseIdent(), this.semicolon());
  for (var i2 = 0; i2 < this.labels.length; ++i2) {
    var n2 = this.labels[i2];
    if (null == e3.label || n2.name === e3.label.name) {
      if (null != n2.kind && (s2 || "loop" === n2.kind))
        break;
      if (e3.label && s2)
        break;
    }
  }
  return i2 === this.labels.length && this.raise(e3.start, "Unsyntactic " + t2), this.finishNode(e3, s2 ? "BreakStatement" : "ContinueStatement");
}, vl.parseDebuggerStatement = function(e3) {
  return this.next(), this.semicolon(), this.finishNode(e3, "DebuggerStatement");
}, vl.parseDoStatement = function(e3) {
  return this.next(), this.labels.push(Sl), e3.body = this.parseStatement("do"), this.labels.pop(), this.expect(Ha._while), e3.test = this.parseParenExpression(), this.options.ecmaVersion >= 6 ? this.eat(Ha.semi) : this.semicolon(), this.finishNode(e3, "DoWhileStatement");
}, vl.parseForStatement = function(e3) {
  this.next();
  var t2 = this.options.ecmaVersion >= 9 && this.canAwait && this.eatContextual("await") ? this.lastTokStart : -1;
  if (this.labels.push(Sl), this.enterScope(0), this.expect(Ha.parenL), this.type === Ha.semi)
    return t2 > -1 && this.unexpected(t2), this.parseFor(e3, null);
  var s2 = this.isLet();
  if (this.type === Ha._var || this.type === Ha._const || s2) {
    var i2 = this.startNode(), n2 = s2 ? "let" : this.value;
    return this.next(), this.parseVar(i2, true, n2), this.finishNode(i2, "VariableDeclaration"), (this.type === Ha._in || this.options.ecmaVersion >= 6 && this.isContextual("of")) && 1 === i2.declarations.length ? (this.options.ecmaVersion >= 9 && (this.type === Ha._in ? t2 > -1 && this.unexpected(t2) : e3.await = t2 > -1), this.parseForIn(e3, i2)) : (t2 > -1 && this.unexpected(t2), this.parseFor(e3, i2));
  }
  var r2 = this.isContextual("let"), o2 = false, a2 = new El(), l2 = this.parseExpression(!(t2 > -1) || "await", a2);
  return this.type === Ha._in || (o2 = this.options.ecmaVersion >= 6 && this.isContextual("of")) ? (this.options.ecmaVersion >= 9 && (this.type === Ha._in ? t2 > -1 && this.unexpected(t2) : e3.await = t2 > -1), r2 && o2 && this.raise(l2.start, "The left-hand side of a for-of loop may not start with 'let'."), this.toAssignable(l2, false, a2), this.checkLValPattern(l2), this.parseForIn(e3, l2)) : (this.checkExpressionErrors(a2, true), t2 > -1 && this.unexpected(t2), this.parseFor(e3, l2));
}, vl.parseFunctionStatement = function(e3, t2, s2) {
  return this.next(), this.parseFunction(e3, wl | (s2 ? 0 : Il), false, t2);
}, vl.parseIfStatement = function(e3) {
  return this.next(), e3.test = this.parseParenExpression(), e3.consequent = this.parseStatement("if"), e3.alternate = this.eat(Ha._else) ? this.parseStatement("if") : null, this.finishNode(e3, "IfStatement");
}, vl.parseReturnStatement = function(e3) {
  return this.inFunction || this.options.allowReturnOutsideFunction || this.raise(this.start, "'return' outside of function"), this.next(), this.eat(Ha.semi) || this.insertSemicolon() ? e3.argument = null : (e3.argument = this.parseExpression(), this.semicolon()), this.finishNode(e3, "ReturnStatement");
}, vl.parseSwitchStatement = function(e3) {
  var t2;
  this.next(), e3.discriminant = this.parseParenExpression(), e3.cases = [], this.expect(Ha.braceL), this.labels.push(kl), this.enterScope(0);
  for (var s2 = false; this.type !== Ha.braceR; )
    if (this.type === Ha._case || this.type === Ha._default) {
      var i2 = this.type === Ha._case;
      t2 && this.finishNode(t2, "SwitchCase"), e3.cases.push(t2 = this.startNode()), t2.consequent = [], this.next(), i2 ? t2.test = this.parseExpression() : (s2 && this.raiseRecoverable(this.lastTokStart, "Multiple default clauses"), s2 = true, t2.test = null), this.expect(Ha.colon);
    } else
      t2 || this.unexpected(), t2.consequent.push(this.parseStatement(null));
  return this.exitScope(), t2 && this.finishNode(t2, "SwitchCase"), this.next(), this.labels.pop(), this.finishNode(e3, "SwitchStatement");
}, vl.parseThrowStatement = function(e3) {
  return this.next(), Ka.test(this.input.slice(this.lastTokEnd, this.start)) && this.raise(this.lastTokEnd, "Illegal newline after throw"), e3.argument = this.parseExpression(), this.semicolon(), this.finishNode(e3, "ThrowStatement");
};
var Al = [];
vl.parseTryStatement = function(e3) {
  if (this.next(), e3.block = this.parseBlock(), e3.handler = null, this.type === Ha._catch) {
    var t2 = this.startNode();
    if (this.next(), this.eat(Ha.parenL)) {
      t2.param = this.parseBindingAtom();
      var s2 = "Identifier" === t2.param.type;
      this.enterScope(s2 ? 32 : 0), this.checkLValPattern(t2.param, s2 ? 4 : 2), this.expect(Ha.parenR);
    } else
      this.options.ecmaVersion < 10 && this.unexpected(), t2.param = null, this.enterScope(0);
    t2.body = this.parseBlock(false), this.exitScope(), e3.handler = this.finishNode(t2, "CatchClause");
  }
  return e3.finalizer = this.eat(Ha._finally) ? this.parseBlock() : null, e3.handler || e3.finalizer || this.raise(e3.start, "Missing catch or finally clause"), this.finishNode(e3, "TryStatement");
}, vl.parseVarStatement = function(e3, t2) {
  return this.next(), this.parseVar(e3, false, t2), this.semicolon(), this.finishNode(e3, "VariableDeclaration");
}, vl.parseWhileStatement = function(e3) {
  return this.next(), e3.test = this.parseParenExpression(), this.labels.push(Sl), e3.body = this.parseStatement("while"), this.labels.pop(), this.finishNode(e3, "WhileStatement");
}, vl.parseWithStatement = function(e3) {
  return this.strict && this.raise(this.start, "'with' in strict mode"), this.next(), e3.object = this.parseParenExpression(), e3.body = this.parseStatement("with"), this.finishNode(e3, "WithStatement");
}, vl.parseEmptyStatement = function(e3) {
  return this.next(), this.finishNode(e3, "EmptyStatement");
}, vl.parseLabeledStatement = function(e3, t2, s2, i2) {
  for (var n2 = 0, r2 = this.labels; n2 < r2.length; n2 += 1) {
    r2[n2].name === t2 && this.raise(s2.start, "Label '" + t2 + "' is already declared");
  }
  for (var o2 = this.type.isLoop ? "loop" : this.type === Ha._switch ? "switch" : null, a2 = this.labels.length - 1; a2 >= 0; a2--) {
    var l2 = this.labels[a2];
    if (l2.statementStart !== e3.start)
      break;
    l2.statementStart = this.start, l2.kind = o2;
  }
  return this.labels.push({ name: t2, kind: o2, statementStart: this.start }), e3.body = this.parseStatement(i2 ? -1 === i2.indexOf("label") ? i2 + "label" : i2 : "label"), this.labels.pop(), e3.label = s2, this.finishNode(e3, "LabeledStatement");
}, vl.parseExpressionStatement = function(e3, t2) {
  return e3.expression = t2, this.semicolon(), this.finishNode(e3, "ExpressionStatement");
}, vl.parseBlock = function(e3, t2, s2) {
  for (void 0 === e3 && (e3 = true), void 0 === t2 && (t2 = this.startNode()), t2.body = [], this.expect(Ha.braceL), e3 && this.enterScope(0); this.type !== Ha.braceR; ) {
    var i2 = this.parseStatement(null);
    t2.body.push(i2);
  }
  return s2 && (this.strict = false), this.next(), e3 && this.exitScope(), this.finishNode(t2, "BlockStatement");
}, vl.parseFor = function(e3, t2) {
  return e3.init = t2, this.expect(Ha.semi), e3.test = this.type === Ha.semi ? null : this.parseExpression(), this.expect(Ha.semi), e3.update = this.type === Ha.parenR ? null : this.parseExpression(), this.expect(Ha.parenR), e3.body = this.parseStatement("for"), this.exitScope(), this.labels.pop(), this.finishNode(e3, "ForStatement");
}, vl.parseForIn = function(e3, t2) {
  var s2 = this.type === Ha._in;
  return this.next(), "VariableDeclaration" === t2.type && null != t2.declarations[0].init && (!s2 || this.options.ecmaVersion < 8 || this.strict || "var" !== t2.kind || "Identifier" !== t2.declarations[0].id.type) && this.raise(t2.start, (s2 ? "for-in" : "for-of") + " loop variable declaration may not have an initializer"), e3.left = t2, e3.right = s2 ? this.parseExpression() : this.parseMaybeAssign(), this.expect(Ha.parenR), e3.body = this.parseStatement("for"), this.exitScope(), this.labels.pop(), this.finishNode(e3, s2 ? "ForInStatement" : "ForOfStatement");
}, vl.parseVar = function(e3, t2, s2) {
  for (e3.declarations = [], e3.kind = s2; ; ) {
    var i2 = this.startNode();
    if (this.parseVarId(i2, s2), this.eat(Ha.eq) ? i2.init = this.parseMaybeAssign(t2) : "const" !== s2 || this.type === Ha._in || this.options.ecmaVersion >= 6 && this.isContextual("of") ? "Identifier" === i2.id.type || t2 && (this.type === Ha._in || this.isContextual("of")) ? i2.init = null : this.raise(this.lastTokEnd, "Complex binding patterns require an initialization value") : this.unexpected(), e3.declarations.push(this.finishNode(i2, "VariableDeclarator")), !this.eat(Ha.comma))
      break;
  }
  return e3;
}, vl.parseVarId = function(e3, t2) {
  e3.id = this.parseBindingAtom(), this.checkLValPattern(e3.id, "var" === t2 ? 1 : 2, false);
};
var wl = 1, Il = 2;
function Pl(e3, t2) {
  var s2 = t2.key.name, i2 = e3[s2], n2 = "true";
  return "MethodDefinition" !== t2.type || "get" !== t2.kind && "set" !== t2.kind || (n2 = (t2.static ? "s" : "i") + t2.kind), "iget" === i2 && "iset" === n2 || "iset" === i2 && "iget" === n2 || "sget" === i2 && "sset" === n2 || "sset" === i2 && "sget" === n2 ? (e3[s2] = "true", false) : !!i2 || (e3[s2] = n2, false);
}
function Cl(e3, t2) {
  var s2 = e3.computed, i2 = e3.key;
  return !s2 && ("Identifier" === i2.type && i2.name === t2 || "Literal" === i2.type && i2.value === t2);
}
vl.parseFunction = function(e3, t2, s2, i2, n2) {
  this.initFunction(e3), (this.options.ecmaVersion >= 9 || this.options.ecmaVersion >= 6 && !i2) && (this.type === Ha.star && t2 & Il && this.unexpected(), e3.generator = this.eat(Ha.star)), this.options.ecmaVersion >= 8 && (e3.async = !!i2), t2 & wl && (e3.id = 4 & t2 && this.type !== Ha.name ? null : this.parseIdent(), !e3.id || t2 & Il || this.checkLValSimple(e3.id, this.strict || e3.generator || e3.async ? this.treatFunctionsAsVar ? 1 : 2 : 3));
  var r2 = this.yieldPos, o2 = this.awaitPos, a2 = this.awaitIdentPos;
  return this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, this.enterScope(ml(e3.async, e3.generator)), t2 & wl || (e3.id = this.type === Ha.name ? this.parseIdent() : null), this.parseFunctionParams(e3), this.parseFunctionBody(e3, s2, false, n2), this.yieldPos = r2, this.awaitPos = o2, this.awaitIdentPos = a2, this.finishNode(e3, t2 & wl ? "FunctionDeclaration" : "FunctionExpression");
}, vl.parseFunctionParams = function(e3) {
  this.expect(Ha.parenL), e3.params = this.parseBindingList(Ha.parenR, false, this.options.ecmaVersion >= 8), this.checkYieldAwaitInDefaultParams();
}, vl.parseClass = function(e3, t2) {
  this.next();
  var s2 = this.strict;
  this.strict = true, this.parseClassId(e3, t2), this.parseClassSuper(e3);
  var i2 = this.enterClassBody(), n2 = this.startNode(), r2 = false;
  for (n2.body = [], this.expect(Ha.braceL); this.type !== Ha.braceR; ) {
    var o2 = this.parseClassElement(null !== e3.superClass);
    o2 && (n2.body.push(o2), "MethodDefinition" === o2.type && "constructor" === o2.kind ? (r2 && this.raise(o2.start, "Duplicate constructor in the same class"), r2 = true) : o2.key && "PrivateIdentifier" === o2.key.type && Pl(i2, o2) && this.raiseRecoverable(o2.key.start, "Identifier '#" + o2.key.name + "' has already been declared"));
  }
  return this.strict = s2, this.next(), e3.body = this.finishNode(n2, "ClassBody"), this.exitClassBody(), this.finishNode(e3, t2 ? "ClassDeclaration" : "ClassExpression");
}, vl.parseClassElement = function(e3) {
  if (this.eat(Ha.semi))
    return null;
  var t2 = this.options.ecmaVersion, s2 = this.startNode(), i2 = "", n2 = false, r2 = false, o2 = "method", a2 = false;
  if (this.eatContextual("static")) {
    if (t2 >= 13 && this.eat(Ha.braceL))
      return this.parseClassStaticBlock(s2), s2;
    this.isClassElementNameStart() || this.type === Ha.star ? a2 = true : i2 = "static";
  }
  if (s2.static = a2, !i2 && t2 >= 8 && this.eatContextual("async") && (!this.isClassElementNameStart() && this.type !== Ha.star || this.canInsertSemicolon() ? i2 = "async" : r2 = true), !i2 && (t2 >= 9 || !r2) && this.eat(Ha.star) && (n2 = true), !i2 && !r2 && !n2) {
    var l2 = this.value;
    (this.eatContextual("get") || this.eatContextual("set")) && (this.isClassElementNameStart() ? o2 = l2 : i2 = l2);
  }
  if (i2 ? (s2.computed = false, s2.key = this.startNodeAt(this.lastTokStart, this.lastTokStartLoc), s2.key.name = i2, this.finishNode(s2.key, "Identifier")) : this.parseClassElementName(s2), t2 < 13 || this.type === Ha.parenL || "method" !== o2 || n2 || r2) {
    var h2 = !s2.static && Cl(s2, "constructor"), c2 = h2 && e3;
    h2 && "method" !== o2 && this.raise(s2.key.start, "Constructor can't have get/set modifier"), s2.kind = h2 ? "constructor" : o2, this.parseClassMethod(s2, n2, r2, c2);
  } else
    this.parseClassField(s2);
  return s2;
}, vl.isClassElementNameStart = function() {
  return this.type === Ha.name || this.type === Ha.privateId || this.type === Ha.num || this.type === Ha.string || this.type === Ha.bracketL || this.type.keyword;
}, vl.parseClassElementName = function(e3) {
  this.type === Ha.privateId ? ("constructor" === this.value && this.raise(this.start, "Classes can't have an element named '#constructor'"), e3.computed = false, e3.key = this.parsePrivateIdent()) : this.parsePropertyName(e3);
}, vl.parseClassMethod = function(e3, t2, s2, i2) {
  var n2 = e3.key;
  "constructor" === e3.kind ? (t2 && this.raise(n2.start, "Constructor can't be a generator"), s2 && this.raise(n2.start, "Constructor can't be an async method")) : e3.static && Cl(e3, "prototype") && this.raise(n2.start, "Classes may not have a static property named prototype");
  var r2 = e3.value = this.parseMethod(t2, s2, i2);
  return "get" === e3.kind && 0 !== r2.params.length && this.raiseRecoverable(r2.start, "getter should have no params"), "set" === e3.kind && 1 !== r2.params.length && this.raiseRecoverable(r2.start, "setter should have exactly one param"), "set" === e3.kind && "RestElement" === r2.params[0].type && this.raiseRecoverable(r2.params[0].start, "Setter cannot use rest params"), this.finishNode(e3, "MethodDefinition");
}, vl.parseClassField = function(e3) {
  if (Cl(e3, "constructor") ? this.raise(e3.key.start, "Classes can't have a field named 'constructor'") : e3.static && Cl(e3, "prototype") && this.raise(e3.key.start, "Classes can't have a static field named 'prototype'"), this.eat(Ha.eq)) {
    var t2 = this.currentThisScope(), s2 = t2.inClassFieldInit;
    t2.inClassFieldInit = true, e3.value = this.parseMaybeAssign(), t2.inClassFieldInit = s2;
  } else
    e3.value = null;
  return this.semicolon(), this.finishNode(e3, "PropertyDefinition");
}, vl.parseClassStaticBlock = function(e3) {
  e3.body = [];
  var t2 = this.labels;
  for (this.labels = [], this.enterScope(320); this.type !== Ha.braceR; ) {
    var s2 = this.parseStatement(null);
    e3.body.push(s2);
  }
  return this.next(), this.exitScope(), this.labels = t2, this.finishNode(e3, "StaticBlock");
}, vl.parseClassId = function(e3, t2) {
  this.type === Ha.name ? (e3.id = this.parseIdent(), t2 && this.checkLValSimple(e3.id, 2, false)) : (true === t2 && this.unexpected(), e3.id = null);
}, vl.parseClassSuper = function(e3) {
  e3.superClass = this.eat(Ha._extends) ? this.parseExprSubscripts(false) : null;
}, vl.enterClassBody = function() {
  var e3 = { declared: /* @__PURE__ */ Object.create(null), used: [] };
  return this.privateNameStack.push(e3), e3.declared;
}, vl.exitClassBody = function() {
  for (var e3 = this.privateNameStack.pop(), t2 = e3.declared, s2 = e3.used, i2 = this.privateNameStack.length, n2 = 0 === i2 ? null : this.privateNameStack[i2 - 1], r2 = 0; r2 < s2.length; ++r2) {
    var o2 = s2[r2];
    il(t2, o2.name) || (n2 ? n2.used.push(o2) : this.raiseRecoverable(o2.start, "Private field '#" + o2.name + "' must be declared in an enclosing class"));
  }
}, vl.parseExport = function(e3, t2) {
  if (this.next(), this.eat(Ha.star))
    return this.options.ecmaVersion >= 11 && (this.eatContextual("as") ? (e3.exported = this.parseModuleExportName(), this.checkExport(t2, e3.exported, this.lastTokStart)) : e3.exported = null), this.expectContextual("from"), this.type !== Ha.string && this.unexpected(), e3.source = this.parseExprAtom(), this.semicolon(), this.finishNode(e3, "ExportAllDeclaration");
  if (this.eat(Ha._default)) {
    var s2;
    if (this.checkExport(t2, "default", this.lastTokStart), this.type === Ha._function || (s2 = this.isAsyncFunction())) {
      var i2 = this.startNode();
      this.next(), s2 && this.next(), e3.declaration = this.parseFunction(i2, 4 | wl, false, s2);
    } else if (this.type === Ha._class) {
      var n2 = this.startNode();
      e3.declaration = this.parseClass(n2, "nullableID");
    } else
      e3.declaration = this.parseMaybeAssign(), this.semicolon();
    return this.finishNode(e3, "ExportDefaultDeclaration");
  }
  if (this.shouldParseExportStatement())
    e3.declaration = this.parseStatement(null), "VariableDeclaration" === e3.declaration.type ? this.checkVariableExport(t2, e3.declaration.declarations) : this.checkExport(t2, e3.declaration.id, e3.declaration.id.start), e3.specifiers = [], e3.source = null;
  else {
    if (e3.declaration = null, e3.specifiers = this.parseExportSpecifiers(t2), this.eatContextual("from"))
      this.type !== Ha.string && this.unexpected(), e3.source = this.parseExprAtom();
    else {
      for (var r2 = 0, o2 = e3.specifiers; r2 < o2.length; r2 += 1) {
        var a2 = o2[r2];
        this.checkUnreserved(a2.local), this.checkLocalExport(a2.local), "Literal" === a2.local.type && this.raise(a2.local.start, "A string literal cannot be used as an exported binding without `from`.");
      }
      e3.source = null;
    }
    this.semicolon();
  }
  return this.finishNode(e3, "ExportNamedDeclaration");
}, vl.checkExport = function(e3, t2, s2) {
  e3 && ("string" != typeof t2 && (t2 = "Identifier" === t2.type ? t2.name : t2.value), il(e3, t2) && this.raiseRecoverable(s2, "Duplicate export '" + t2 + "'"), e3[t2] = true);
}, vl.checkPatternExport = function(e3, t2) {
  var s2 = t2.type;
  if ("Identifier" === s2)
    this.checkExport(e3, t2, t2.start);
  else if ("ObjectPattern" === s2)
    for (var i2 = 0, n2 = t2.properties; i2 < n2.length; i2 += 1) {
      var r2 = n2[i2];
      this.checkPatternExport(e3, r2);
    }
  else if ("ArrayPattern" === s2)
    for (var o2 = 0, a2 = t2.elements; o2 < a2.length; o2 += 1) {
      var l2 = a2[o2];
      l2 && this.checkPatternExport(e3, l2);
    }
  else
    "Property" === s2 ? this.checkPatternExport(e3, t2.value) : "AssignmentPattern" === s2 ? this.checkPatternExport(e3, t2.left) : "RestElement" === s2 ? this.checkPatternExport(e3, t2.argument) : "ParenthesizedExpression" === s2 && this.checkPatternExport(e3, t2.expression);
}, vl.checkVariableExport = function(e3, t2) {
  if (e3)
    for (var s2 = 0, i2 = t2; s2 < i2.length; s2 += 1) {
      var n2 = i2[s2];
      this.checkPatternExport(e3, n2.id);
    }
}, vl.shouldParseExportStatement = function() {
  return "var" === this.type.keyword || "const" === this.type.keyword || "class" === this.type.keyword || "function" === this.type.keyword || this.isLet() || this.isAsyncFunction();
}, vl.parseExportSpecifiers = function(e3) {
  var t2 = [], s2 = true;
  for (this.expect(Ha.braceL); !this.eat(Ha.braceR); ) {
    if (s2)
      s2 = false;
    else if (this.expect(Ha.comma), this.afterTrailingComma(Ha.braceR))
      break;
    var i2 = this.startNode();
    i2.local = this.parseModuleExportName(), i2.exported = this.eatContextual("as") ? this.parseModuleExportName() : i2.local, this.checkExport(e3, i2.exported, i2.exported.start), t2.push(this.finishNode(i2, "ExportSpecifier"));
  }
  return t2;
}, vl.parseImport = function(e3) {
  return this.next(), this.type === Ha.string ? (e3.specifiers = Al, e3.source = this.parseExprAtom()) : (e3.specifiers = this.parseImportSpecifiers(), this.expectContextual("from"), e3.source = this.type === Ha.string ? this.parseExprAtom() : this.unexpected()), this.semicolon(), this.finishNode(e3, "ImportDeclaration");
}, vl.parseImportSpecifiers = function() {
  var e3 = [], t2 = true;
  if (this.type === Ha.name) {
    var s2 = this.startNode();
    if (s2.local = this.parseIdent(), this.checkLValSimple(s2.local, 2), e3.push(this.finishNode(s2, "ImportDefaultSpecifier")), !this.eat(Ha.comma))
      return e3;
  }
  if (this.type === Ha.star) {
    var i2 = this.startNode();
    return this.next(), this.expectContextual("as"), i2.local = this.parseIdent(), this.checkLValSimple(i2.local, 2), e3.push(this.finishNode(i2, "ImportNamespaceSpecifier")), e3;
  }
  for (this.expect(Ha.braceL); !this.eat(Ha.braceR); ) {
    if (t2)
      t2 = false;
    else if (this.expect(Ha.comma), this.afterTrailingComma(Ha.braceR))
      break;
    var n2 = this.startNode();
    n2.imported = this.parseModuleExportName(), this.eatContextual("as") ? n2.local = this.parseIdent() : (this.checkUnreserved(n2.imported), n2.local = n2.imported), this.checkLValSimple(n2.local, 2), e3.push(this.finishNode(n2, "ImportSpecifier"));
  }
  return e3;
}, vl.parseModuleExportName = function() {
  if (this.options.ecmaVersion >= 13 && this.type === Ha.string) {
    var e3 = this.parseLiteral(this.value);
    return al.test(e3.value) && this.raise(e3.start, "An export name cannot include a lone surrogate."), e3;
  }
  return this.parseIdent(true);
}, vl.adaptDirectivePrologue = function(e3) {
  for (var t2 = 0; t2 < e3.length && this.isDirectiveCandidate(e3[t2]); ++t2)
    e3[t2].directive = e3[t2].expression.raw.slice(1, -1);
}, vl.isDirectiveCandidate = function(e3) {
  return this.options.ecmaVersion >= 5 && "ExpressionStatement" === e3.type && "Literal" === e3.expression.type && "string" == typeof e3.expression.value && ('"' === this.input[e3.start] || "'" === this.input[e3.start]);
};
var $l = gl.prototype;
$l.toAssignable = function(e3, t2, s2) {
  if (this.options.ecmaVersion >= 6 && e3)
    switch (e3.type) {
      case "Identifier":
        this.inAsync && "await" === e3.name && this.raise(e3.start, "Cannot use 'await' as identifier inside an async function");
        break;
      case "ObjectPattern":
      case "ArrayPattern":
      case "AssignmentPattern":
      case "RestElement":
        break;
      case "ObjectExpression":
        e3.type = "ObjectPattern", s2 && this.checkPatternErrors(s2, true);
        for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
          var r2 = n2[i2];
          this.toAssignable(r2, t2), "RestElement" !== r2.type || "ArrayPattern" !== r2.argument.type && "ObjectPattern" !== r2.argument.type || this.raise(r2.argument.start, "Unexpected token");
        }
        break;
      case "Property":
        "init" !== e3.kind && this.raise(e3.key.start, "Object pattern can't contain getter or setter"), this.toAssignable(e3.value, t2);
        break;
      case "ArrayExpression":
        e3.type = "ArrayPattern", s2 && this.checkPatternErrors(s2, true), this.toAssignableList(e3.elements, t2);
        break;
      case "SpreadElement":
        e3.type = "RestElement", this.toAssignable(e3.argument, t2), "AssignmentPattern" === e3.argument.type && this.raise(e3.argument.start, "Rest elements cannot have a default value");
        break;
      case "AssignmentExpression":
        "=" !== e3.operator && this.raise(e3.left.end, "Only '=' operator can be used for specifying default value."), e3.type = "AssignmentPattern", delete e3.operator, this.toAssignable(e3.left, t2);
        break;
      case "ParenthesizedExpression":
        this.toAssignable(e3.expression, t2, s2);
        break;
      case "ChainExpression":
        this.raiseRecoverable(e3.start, "Optional chaining cannot appear in left-hand side");
        break;
      case "MemberExpression":
        if (!t2)
          break;
      default:
        this.raise(e3.start, "Assigning to rvalue");
    }
  else
    s2 && this.checkPatternErrors(s2, true);
  return e3;
}, $l.toAssignableList = function(e3, t2) {
  for (var s2 = e3.length, i2 = 0; i2 < s2; i2++) {
    var n2 = e3[i2];
    n2 && this.toAssignable(n2, t2);
  }
  if (s2) {
    var r2 = e3[s2 - 1];
    6 === this.options.ecmaVersion && t2 && r2 && "RestElement" === r2.type && "Identifier" !== r2.argument.type && this.unexpected(r2.argument.start);
  }
  return e3;
}, $l.parseSpread = function(e3) {
  var t2 = this.startNode();
  return this.next(), t2.argument = this.parseMaybeAssign(false, e3), this.finishNode(t2, "SpreadElement");
}, $l.parseRestBinding = function() {
  var e3 = this.startNode();
  return this.next(), 6 === this.options.ecmaVersion && this.type !== Ha.name && this.unexpected(), e3.argument = this.parseBindingAtom(), this.finishNode(e3, "RestElement");
}, $l.parseBindingAtom = function() {
  if (this.options.ecmaVersion >= 6)
    switch (this.type) {
      case Ha.bracketL:
        var e3 = this.startNode();
        return this.next(), e3.elements = this.parseBindingList(Ha.bracketR, true, true), this.finishNode(e3, "ArrayPattern");
      case Ha.braceL:
        return this.parseObj(true);
    }
  return this.parseIdent();
}, $l.parseBindingList = function(e3, t2, s2) {
  for (var i2 = [], n2 = true; !this.eat(e3); )
    if (n2 ? n2 = false : this.expect(Ha.comma), t2 && this.type === Ha.comma)
      i2.push(null);
    else {
      if (s2 && this.afterTrailingComma(e3))
        break;
      if (this.type === Ha.ellipsis) {
        var r2 = this.parseRestBinding();
        this.parseBindingListItem(r2), i2.push(r2), this.type === Ha.comma && this.raise(this.start, "Comma is not permitted after the rest element"), this.expect(e3);
        break;
      }
      var o2 = this.parseMaybeDefault(this.start, this.startLoc);
      this.parseBindingListItem(o2), i2.push(o2);
    }
  return i2;
}, $l.parseBindingListItem = function(e3) {
  return e3;
}, $l.parseMaybeDefault = function(e3, t2, s2) {
  if (s2 = s2 || this.parseBindingAtom(), this.options.ecmaVersion < 6 || !this.eat(Ha.eq))
    return s2;
  var i2 = this.startNodeAt(e3, t2);
  return i2.left = s2, i2.right = this.parseMaybeAssign(), this.finishNode(i2, "AssignmentPattern");
}, $l.checkLValSimple = function(e3, t2, s2) {
  void 0 === t2 && (t2 = 0);
  var i2 = 0 !== t2;
  switch (e3.type) {
    case "Identifier":
      this.strict && this.reservedWordsStrictBind.test(e3.name) && this.raiseRecoverable(e3.start, (i2 ? "Binding " : "Assigning to ") + e3.name + " in strict mode"), i2 && (2 === t2 && "let" === e3.name && this.raiseRecoverable(e3.start, "let is disallowed as a lexically bound name"), s2 && (il(s2, e3.name) && this.raiseRecoverable(e3.start, "Argument name clash"), s2[e3.name] = true), 5 !== t2 && this.declareName(e3.name, t2, e3.start));
      break;
    case "ChainExpression":
      this.raiseRecoverable(e3.start, "Optional chaining cannot appear in left-hand side");
      break;
    case "MemberExpression":
      i2 && this.raiseRecoverable(e3.start, "Binding member expression");
      break;
    case "ParenthesizedExpression":
      return i2 && this.raiseRecoverable(e3.start, "Binding parenthesized expression"), this.checkLValSimple(e3.expression, t2, s2);
    default:
      this.raise(e3.start, (i2 ? "Binding" : "Assigning to") + " rvalue");
  }
}, $l.checkLValPattern = function(e3, t2, s2) {
  switch (void 0 === t2 && (t2 = 0), e3.type) {
    case "ObjectPattern":
      for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
        var r2 = n2[i2];
        this.checkLValInnerPattern(r2, t2, s2);
      }
      break;
    case "ArrayPattern":
      for (var o2 = 0, a2 = e3.elements; o2 < a2.length; o2 += 1) {
        var l2 = a2[o2];
        l2 && this.checkLValInnerPattern(l2, t2, s2);
      }
      break;
    default:
      this.checkLValSimple(e3, t2, s2);
  }
}, $l.checkLValInnerPattern = function(e3, t2, s2) {
  switch (void 0 === t2 && (t2 = 0), e3.type) {
    case "Property":
      this.checkLValInnerPattern(e3.value, t2, s2);
      break;
    case "AssignmentPattern":
      this.checkLValPattern(e3.left, t2, s2);
      break;
    case "RestElement":
      this.checkLValPattern(e3.argument, t2, s2);
      break;
    default:
      this.checkLValPattern(e3, t2, s2);
  }
};
var Nl = function(e3, t2, s2, i2, n2) {
  this.token = e3, this.isExpr = !!t2, this.preserveSpace = !!s2, this.override = i2, this.generator = !!n2;
}, _l = { b_stat: new Nl("{", false), b_expr: new Nl("{", true), b_tmpl: new Nl("${", false), p_stat: new Nl("(", false), p_expr: new Nl("(", true), q_tmpl: new Nl("`", true, true, function(e3) {
  return e3.tryReadTemplateToken();
}), f_stat: new Nl("function", false), f_expr: new Nl("function", true), f_expr_gen: new Nl("function", true, false, null, true), f_gen: new Nl("function", false, false, null, true) }, Tl = gl.prototype;
Tl.initialContext = function() {
  return [_l.b_stat];
}, Tl.curContext = function() {
  return this.context[this.context.length - 1];
}, Tl.braceIsBlock = function(e3) {
  var t2 = this.curContext();
  return t2 === _l.f_expr || t2 === _l.f_stat || (e3 !== Ha.colon || t2 !== _l.b_stat && t2 !== _l.b_expr ? e3 === Ha._return || e3 === Ha.name && this.exprAllowed ? Ka.test(this.input.slice(this.lastTokEnd, this.start)) : e3 === Ha._else || e3 === Ha.semi || e3 === Ha.eof || e3 === Ha.parenR || e3 === Ha.arrow || (e3 === Ha.braceL ? t2 === _l.b_stat : e3 !== Ha._var && e3 !== Ha._const && e3 !== Ha.name && !this.exprAllowed) : !t2.isExpr);
}, Tl.inGeneratorContext = function() {
  for (var e3 = this.context.length - 1; e3 >= 1; e3--) {
    var t2 = this.context[e3];
    if ("function" === t2.token)
      return t2.generator;
  }
  return false;
}, Tl.updateContext = function(e3) {
  var t2, s2 = this.type;
  s2.keyword && e3 === Ha.dot ? this.exprAllowed = false : (t2 = s2.updateContext) ? t2.call(this, e3) : this.exprAllowed = s2.beforeExpr;
}, Tl.overrideContext = function(e3) {
  this.curContext() !== e3 && (this.context[this.context.length - 1] = e3);
}, Ha.parenR.updateContext = Ha.braceR.updateContext = function() {
  if (1 !== this.context.length) {
    var e3 = this.context.pop();
    e3 === _l.b_stat && "function" === this.curContext().token && (e3 = this.context.pop()), this.exprAllowed = !e3.isExpr;
  } else
    this.exprAllowed = true;
}, Ha.braceL.updateContext = function(e3) {
  this.context.push(this.braceIsBlock(e3) ? _l.b_stat : _l.b_expr), this.exprAllowed = true;
}, Ha.dollarBraceL.updateContext = function() {
  this.context.push(_l.b_tmpl), this.exprAllowed = true;
}, Ha.parenL.updateContext = function(e3) {
  var t2 = e3 === Ha._if || e3 === Ha._for || e3 === Ha._with || e3 === Ha._while;
  this.context.push(t2 ? _l.p_stat : _l.p_expr), this.exprAllowed = true;
}, Ha.incDec.updateContext = function() {
}, Ha._function.updateContext = Ha._class.updateContext = function(e3) {
  !e3.beforeExpr || e3 === Ha._else || e3 === Ha.semi && this.curContext() !== _l.p_stat || e3 === Ha._return && Ka.test(this.input.slice(this.lastTokEnd, this.start)) || (e3 === Ha.colon || e3 === Ha.braceL) && this.curContext() === _l.b_stat ? this.context.push(_l.f_stat) : this.context.push(_l.f_expr), this.exprAllowed = false;
}, Ha.backQuote.updateContext = function() {
  this.curContext() === _l.q_tmpl ? this.context.pop() : this.context.push(_l.q_tmpl), this.exprAllowed = false;
}, Ha.star.updateContext = function(e3) {
  if (e3 === Ha._function) {
    var t2 = this.context.length - 1;
    this.context[t2] === _l.f_expr ? this.context[t2] = _l.f_expr_gen : this.context[t2] = _l.f_gen;
  }
  this.exprAllowed = true;
}, Ha.name.updateContext = function(e3) {
  var t2 = false;
  this.options.ecmaVersion >= 6 && e3 !== Ha.dot && ("of" === this.value && !this.exprAllowed || "yield" === this.value && this.inGeneratorContext()) && (t2 = true), this.exprAllowed = t2;
};
var Rl = gl.prototype;
function Ml(e3) {
  return "MemberExpression" === e3.type && "PrivateIdentifier" === e3.property.type || "ChainExpression" === e3.type && Ml(e3.expression);
}
Rl.checkPropClash = function(e3, t2, s2) {
  if (!(this.options.ecmaVersion >= 9 && "SpreadElement" === e3.type || this.options.ecmaVersion >= 6 && (e3.computed || e3.method || e3.shorthand))) {
    var i2, n2 = e3.key;
    switch (n2.type) {
      case "Identifier":
        i2 = n2.name;
        break;
      case "Literal":
        i2 = String(n2.value);
        break;
      default:
        return;
    }
    var r2 = e3.kind;
    if (this.options.ecmaVersion >= 6)
      "__proto__" === i2 && "init" === r2 && (t2.proto && (s2 ? s2.doubleProto < 0 && (s2.doubleProto = n2.start) : this.raiseRecoverable(n2.start, "Redefinition of __proto__ property")), t2.proto = true);
    else {
      var o2 = t2[i2 = "$" + i2];
      if (o2)
        ("init" === r2 ? this.strict && o2.init || o2.get || o2.set : o2.init || o2[r2]) && this.raiseRecoverable(n2.start, "Redefinition of property");
      else
        o2 = t2[i2] = { init: false, get: false, set: false };
      o2[r2] = true;
    }
  }
}, Rl.parseExpression = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseMaybeAssign(e3, t2);
  if (this.type === Ha.comma) {
    var r2 = this.startNodeAt(s2, i2);
    for (r2.expressions = [n2]; this.eat(Ha.comma); )
      r2.expressions.push(this.parseMaybeAssign(e3, t2));
    return this.finishNode(r2, "SequenceExpression");
  }
  return n2;
}, Rl.parseMaybeAssign = function(e3, t2, s2) {
  if (this.isContextual("yield")) {
    if (this.inGenerator)
      return this.parseYield(e3);
    this.exprAllowed = false;
  }
  var i2 = false, n2 = -1, r2 = -1, o2 = -1;
  t2 ? (n2 = t2.parenthesizedAssign, r2 = t2.trailingComma, o2 = t2.doubleProto, t2.parenthesizedAssign = t2.trailingComma = -1) : (t2 = new El(), i2 = true);
  var a2 = this.start, l2 = this.startLoc;
  this.type !== Ha.parenL && this.type !== Ha.name || (this.potentialArrowAt = this.start, this.potentialArrowInForAwait = "await" === e3);
  var h2 = this.parseMaybeConditional(e3, t2);
  if (s2 && (h2 = s2.call(this, h2, a2, l2)), this.type.isAssign) {
    var c2 = this.startNodeAt(a2, l2);
    return c2.operator = this.value, this.type === Ha.eq && (h2 = this.toAssignable(h2, false, t2)), i2 || (t2.parenthesizedAssign = t2.trailingComma = t2.doubleProto = -1), t2.shorthandAssign >= h2.start && (t2.shorthandAssign = -1), this.type === Ha.eq ? this.checkLValPattern(h2) : this.checkLValSimple(h2), c2.left = h2, this.next(), c2.right = this.parseMaybeAssign(e3), o2 > -1 && (t2.doubleProto = o2), this.finishNode(c2, "AssignmentExpression");
  }
  return i2 && this.checkExpressionErrors(t2, true), n2 > -1 && (t2.parenthesizedAssign = n2), r2 > -1 && (t2.trailingComma = r2), h2;
}, Rl.parseMaybeConditional = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseExprOps(e3, t2);
  if (this.checkExpressionErrors(t2))
    return n2;
  if (this.eat(Ha.question)) {
    var r2 = this.startNodeAt(s2, i2);
    return r2.test = n2, r2.consequent = this.parseMaybeAssign(), this.expect(Ha.colon), r2.alternate = this.parseMaybeAssign(e3), this.finishNode(r2, "ConditionalExpression");
  }
  return n2;
}, Rl.parseExprOps = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseMaybeUnary(t2, false, false, e3);
  return this.checkExpressionErrors(t2) || n2.start === s2 && "ArrowFunctionExpression" === n2.type ? n2 : this.parseExprOp(n2, s2, i2, -1, e3);
}, Rl.parseExprOp = function(e3, t2, s2, i2, n2) {
  var r2 = this.type.binop;
  if (null != r2 && (!n2 || this.type !== Ha._in) && r2 > i2) {
    var o2 = this.type === Ha.logicalOR || this.type === Ha.logicalAND, a2 = this.type === Ha.coalesce;
    a2 && (r2 = Ha.logicalAND.binop);
    var l2 = this.value;
    this.next();
    var h2 = this.start, c2 = this.startLoc, u2 = this.parseExprOp(this.parseMaybeUnary(null, false, false, n2), h2, c2, r2, n2), d2 = this.buildBinary(t2, s2, e3, u2, l2, o2 || a2);
    return (o2 && this.type === Ha.coalesce || a2 && (this.type === Ha.logicalOR || this.type === Ha.logicalAND)) && this.raiseRecoverable(this.start, "Logical expressions and coalesce expressions cannot be mixed. Wrap either by parentheses"), this.parseExprOp(d2, t2, s2, i2, n2);
  }
  return e3;
}, Rl.buildBinary = function(e3, t2, s2, i2, n2, r2) {
  "PrivateIdentifier" === i2.type && this.raise(i2.start, "Private identifier can only be left side of binary expression");
  var o2 = this.startNodeAt(e3, t2);
  return o2.left = s2, o2.operator = n2, o2.right = i2, this.finishNode(o2, r2 ? "LogicalExpression" : "BinaryExpression");
}, Rl.parseMaybeUnary = function(e3, t2, s2, i2) {
  var n2, r2 = this.start, o2 = this.startLoc;
  if (this.isContextual("await") && this.canAwait)
    n2 = this.parseAwait(i2), t2 = true;
  else if (this.type.prefix) {
    var a2 = this.startNode(), l2 = this.type === Ha.incDec;
    a2.operator = this.value, a2.prefix = true, this.next(), a2.argument = this.parseMaybeUnary(null, true, l2, i2), this.checkExpressionErrors(e3, true), l2 ? this.checkLValSimple(a2.argument) : this.strict && "delete" === a2.operator && "Identifier" === a2.argument.type ? this.raiseRecoverable(a2.start, "Deleting local variable in strict mode") : "delete" === a2.operator && Ml(a2.argument) ? this.raiseRecoverable(a2.start, "Private fields can not be deleted") : t2 = true, n2 = this.finishNode(a2, l2 ? "UpdateExpression" : "UnaryExpression");
  } else if (t2 || this.type !== Ha.privateId) {
    if (n2 = this.parseExprSubscripts(e3, i2), this.checkExpressionErrors(e3))
      return n2;
    for (; this.type.postfix && !this.canInsertSemicolon(); ) {
      var h2 = this.startNodeAt(r2, o2);
      h2.operator = this.value, h2.prefix = false, h2.argument = n2, this.checkLValSimple(n2), this.next(), n2 = this.finishNode(h2, "UpdateExpression");
    }
  } else
    (i2 || 0 === this.privateNameStack.length) && this.unexpected(), n2 = this.parsePrivateIdent(), this.type !== Ha._in && this.unexpected();
  return s2 || !this.eat(Ha.starstar) ? n2 : t2 ? void this.unexpected(this.lastTokStart) : this.buildBinary(r2, o2, n2, this.parseMaybeUnary(null, false, false, i2), "**", false);
}, Rl.parseExprSubscripts = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseExprAtom(e3, t2);
  if ("ArrowFunctionExpression" === n2.type && ")" !== this.input.slice(this.lastTokStart, this.lastTokEnd))
    return n2;
  var r2 = this.parseSubscripts(n2, s2, i2, false, t2);
  return e3 && "MemberExpression" === r2.type && (e3.parenthesizedAssign >= r2.start && (e3.parenthesizedAssign = -1), e3.parenthesizedBind >= r2.start && (e3.parenthesizedBind = -1), e3.trailingComma >= r2.start && (e3.trailingComma = -1)), r2;
}, Rl.parseSubscripts = function(e3, t2, s2, i2, n2) {
  for (var r2 = this.options.ecmaVersion >= 8 && "Identifier" === e3.type && "async" === e3.name && this.lastTokEnd === e3.end && !this.canInsertSemicolon() && e3.end - e3.start == 5 && this.potentialArrowAt === e3.start, o2 = false; ; ) {
    var a2 = this.parseSubscript(e3, t2, s2, i2, r2, o2, n2);
    if (a2.optional && (o2 = true), a2 === e3 || "ArrowFunctionExpression" === a2.type) {
      if (o2) {
        var l2 = this.startNodeAt(t2, s2);
        l2.expression = a2, a2 = this.finishNode(l2, "ChainExpression");
      }
      return a2;
    }
    e3 = a2;
  }
}, Rl.parseSubscript = function(e3, t2, s2, i2, n2, r2, o2) {
  var a2 = this.options.ecmaVersion >= 11, l2 = a2 && this.eat(Ha.questionDot);
  i2 && l2 && this.raise(this.lastTokStart, "Optional chaining cannot appear in the callee of new expressions");
  var h2 = this.eat(Ha.bracketL);
  if (h2 || l2 && this.type !== Ha.parenL && this.type !== Ha.backQuote || this.eat(Ha.dot)) {
    var c2 = this.startNodeAt(t2, s2);
    c2.object = e3, h2 ? (c2.property = this.parseExpression(), this.expect(Ha.bracketR)) : this.type === Ha.privateId && "Super" !== e3.type ? c2.property = this.parsePrivateIdent() : c2.property = this.parseIdent("never" !== this.options.allowReserved), c2.computed = !!h2, a2 && (c2.optional = l2), e3 = this.finishNode(c2, "MemberExpression");
  } else if (!i2 && this.eat(Ha.parenL)) {
    var u2 = new El(), d2 = this.yieldPos, p2 = this.awaitPos, f2 = this.awaitIdentPos;
    this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0;
    var m2 = this.parseExprList(Ha.parenR, this.options.ecmaVersion >= 8, false, u2);
    if (n2 && !l2 && !this.canInsertSemicolon() && this.eat(Ha.arrow))
      return this.checkPatternErrors(u2, false), this.checkYieldAwaitInDefaultParams(), this.awaitIdentPos > 0 && this.raise(this.awaitIdentPos, "Cannot use 'await' as identifier inside an async function"), this.yieldPos = d2, this.awaitPos = p2, this.awaitIdentPos = f2, this.parseArrowExpression(this.startNodeAt(t2, s2), m2, true, o2);
    this.checkExpressionErrors(u2, true), this.yieldPos = d2 || this.yieldPos, this.awaitPos = p2 || this.awaitPos, this.awaitIdentPos = f2 || this.awaitIdentPos;
    var g2 = this.startNodeAt(t2, s2);
    g2.callee = e3, g2.arguments = m2, a2 && (g2.optional = l2), e3 = this.finishNode(g2, "CallExpression");
  } else if (this.type === Ha.backQuote) {
    (l2 || r2) && this.raise(this.start, "Optional chaining cannot appear in the tag of tagged template expressions");
    var y2 = this.startNodeAt(t2, s2);
    y2.tag = e3, y2.quasi = this.parseTemplate({ isTagged: true }), e3 = this.finishNode(y2, "TaggedTemplateExpression");
  }
  return e3;
}, Rl.parseExprAtom = function(e3, t2) {
  this.type === Ha.slash && this.readRegexp();
  var s2, i2 = this.potentialArrowAt === this.start;
  switch (this.type) {
    case Ha._super:
      return this.allowSuper || this.raise(this.start, "'super' keyword outside a method"), s2 = this.startNode(), this.next(), this.type !== Ha.parenL || this.allowDirectSuper || this.raise(s2.start, "super() call outside constructor of a subclass"), this.type !== Ha.dot && this.type !== Ha.bracketL && this.type !== Ha.parenL && this.unexpected(), this.finishNode(s2, "Super");
    case Ha._this:
      return s2 = this.startNode(), this.next(), this.finishNode(s2, "ThisExpression");
    case Ha.name:
      var n2 = this.start, r2 = this.startLoc, o2 = this.containsEsc, a2 = this.parseIdent(false);
      if (this.options.ecmaVersion >= 8 && !o2 && "async" === a2.name && !this.canInsertSemicolon() && this.eat(Ha._function))
        return this.overrideContext(_l.f_expr), this.parseFunction(this.startNodeAt(n2, r2), 0, false, true, t2);
      if (i2 && !this.canInsertSemicolon()) {
        if (this.eat(Ha.arrow))
          return this.parseArrowExpression(this.startNodeAt(n2, r2), [a2], false, t2);
        if (this.options.ecmaVersion >= 8 && "async" === a2.name && this.type === Ha.name && !o2 && (!this.potentialArrowInForAwait || "of" !== this.value || this.containsEsc))
          return a2 = this.parseIdent(false), !this.canInsertSemicolon() && this.eat(Ha.arrow) || this.unexpected(), this.parseArrowExpression(this.startNodeAt(n2, r2), [a2], true, t2);
      }
      return a2;
    case Ha.regexp:
      var l2 = this.value;
      return (s2 = this.parseLiteral(l2.value)).regex = { pattern: l2.pattern, flags: l2.flags }, s2;
    case Ha.num:
    case Ha.string:
      return this.parseLiteral(this.value);
    case Ha._null:
    case Ha._true:
    case Ha._false:
      return (s2 = this.startNode()).value = this.type === Ha._null ? null : this.type === Ha._true, s2.raw = this.type.keyword, this.next(), this.finishNode(s2, "Literal");
    case Ha.parenL:
      var h2 = this.start, c2 = this.parseParenAndDistinguishExpression(i2, t2);
      return e3 && (e3.parenthesizedAssign < 0 && !this.isSimpleAssignTarget(c2) && (e3.parenthesizedAssign = h2), e3.parenthesizedBind < 0 && (e3.parenthesizedBind = h2)), c2;
    case Ha.bracketL:
      return s2 = this.startNode(), this.next(), s2.elements = this.parseExprList(Ha.bracketR, true, true, e3), this.finishNode(s2, "ArrayExpression");
    case Ha.braceL:
      return this.overrideContext(_l.b_expr), this.parseObj(false, e3);
    case Ha._function:
      return s2 = this.startNode(), this.next(), this.parseFunction(s2, 0);
    case Ha._class:
      return this.parseClass(this.startNode(), false);
    case Ha._new:
      return this.parseNew();
    case Ha.backQuote:
      return this.parseTemplate();
    case Ha._import:
      return this.options.ecmaVersion >= 11 ? this.parseExprImport() : this.unexpected();
    default:
      this.unexpected();
  }
}, Rl.parseExprImport = function() {
  var e3 = this.startNode();
  this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword import");
  var t2 = this.parseIdent(true);
  switch (this.type) {
    case Ha.parenL:
      return this.parseDynamicImport(e3);
    case Ha.dot:
      return e3.meta = t2, this.parseImportMeta(e3);
    default:
      this.unexpected();
  }
}, Rl.parseDynamicImport = function(e3) {
  if (this.next(), e3.source = this.parseMaybeAssign(), !this.eat(Ha.parenR)) {
    var t2 = this.start;
    this.eat(Ha.comma) && this.eat(Ha.parenR) ? this.raiseRecoverable(t2, "Trailing comma is not allowed in import()") : this.unexpected(t2);
  }
  return this.finishNode(e3, "ImportExpression");
}, Rl.parseImportMeta = function(e3) {
  this.next();
  var t2 = this.containsEsc;
  return e3.property = this.parseIdent(true), "meta" !== e3.property.name && this.raiseRecoverable(e3.property.start, "The only valid meta property for import is 'import.meta'"), t2 && this.raiseRecoverable(e3.start, "'import.meta' must not contain escaped characters"), "module" === this.options.sourceType || this.options.allowImportExportEverywhere || this.raiseRecoverable(e3.start, "Cannot use 'import.meta' outside a module"), this.finishNode(e3, "MetaProperty");
}, Rl.parseLiteral = function(e3) {
  var t2 = this.startNode();
  return t2.value = e3, t2.raw = this.input.slice(this.start, this.end), 110 === t2.raw.charCodeAt(t2.raw.length - 1) && (t2.bigint = t2.raw.slice(0, -1).replace(/_/g, "")), this.next(), this.finishNode(t2, "Literal");
}, Rl.parseParenExpression = function() {
  this.expect(Ha.parenL);
  var e3 = this.parseExpression();
  return this.expect(Ha.parenR), e3;
}, Rl.parseParenAndDistinguishExpression = function(e3, t2) {
  var s2, i2 = this.start, n2 = this.startLoc, r2 = this.options.ecmaVersion >= 8;
  if (this.options.ecmaVersion >= 6) {
    this.next();
    var o2, a2 = this.start, l2 = this.startLoc, h2 = [], c2 = true, u2 = false, d2 = new El(), p2 = this.yieldPos, f2 = this.awaitPos;
    for (this.yieldPos = 0, this.awaitPos = 0; this.type !== Ha.parenR; ) {
      if (c2 ? c2 = false : this.expect(Ha.comma), r2 && this.afterTrailingComma(Ha.parenR, true)) {
        u2 = true;
        break;
      }
      if (this.type === Ha.ellipsis) {
        o2 = this.start, h2.push(this.parseParenItem(this.parseRestBinding())), this.type === Ha.comma && this.raise(this.start, "Comma is not permitted after the rest element");
        break;
      }
      h2.push(this.parseMaybeAssign(false, d2, this.parseParenItem));
    }
    var m2 = this.lastTokEnd, g2 = this.lastTokEndLoc;
    if (this.expect(Ha.parenR), e3 && !this.canInsertSemicolon() && this.eat(Ha.arrow))
      return this.checkPatternErrors(d2, false), this.checkYieldAwaitInDefaultParams(), this.yieldPos = p2, this.awaitPos = f2, this.parseParenArrowList(i2, n2, h2, t2);
    h2.length && !u2 || this.unexpected(this.lastTokStart), o2 && this.unexpected(o2), this.checkExpressionErrors(d2, true), this.yieldPos = p2 || this.yieldPos, this.awaitPos = f2 || this.awaitPos, h2.length > 1 ? ((s2 = this.startNodeAt(a2, l2)).expressions = h2, this.finishNodeAt(s2, "SequenceExpression", m2, g2)) : s2 = h2[0];
  } else
    s2 = this.parseParenExpression();
  if (this.options.preserveParens) {
    var y2 = this.startNodeAt(i2, n2);
    return y2.expression = s2, this.finishNode(y2, "ParenthesizedExpression");
  }
  return s2;
}, Rl.parseParenItem = function(e3) {
  return e3;
}, Rl.parseParenArrowList = function(e3, t2, s2, i2) {
  return this.parseArrowExpression(this.startNodeAt(e3, t2), s2, false, i2);
};
var Ol = [];
Rl.parseNew = function() {
  this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword new");
  var e3 = this.startNode(), t2 = this.parseIdent(true);
  if (this.options.ecmaVersion >= 6 && this.eat(Ha.dot)) {
    e3.meta = t2;
    var s2 = this.containsEsc;
    return e3.property = this.parseIdent(true), "target" !== e3.property.name && this.raiseRecoverable(e3.property.start, "The only valid meta property for new is 'new.target'"), s2 && this.raiseRecoverable(e3.start, "'new.target' must not contain escaped characters"), this.allowNewDotTarget || this.raiseRecoverable(e3.start, "'new.target' can only be used in functions and class static block"), this.finishNode(e3, "MetaProperty");
  }
  var i2 = this.start, n2 = this.startLoc, r2 = this.type === Ha._import;
  return e3.callee = this.parseSubscripts(this.parseExprAtom(), i2, n2, true, false), r2 && "ImportExpression" === e3.callee.type && this.raise(i2, "Cannot use new with import()"), this.eat(Ha.parenL) ? e3.arguments = this.parseExprList(Ha.parenR, this.options.ecmaVersion >= 8, false) : e3.arguments = Ol, this.finishNode(e3, "NewExpression");
}, Rl.parseTemplateElement = function(e3) {
  var t2 = e3.isTagged, s2 = this.startNode();
  return this.type === Ha.invalidTemplate ? (t2 || this.raiseRecoverable(this.start, "Bad escape sequence in untagged template literal"), s2.value = { raw: this.value, cooked: null }) : s2.value = { raw: this.input.slice(this.start, this.end).replace(/\r\n?/g, "\n"), cooked: this.value }, this.next(), s2.tail = this.type === Ha.backQuote, this.finishNode(s2, "TemplateElement");
}, Rl.parseTemplate = function(e3) {
  void 0 === e3 && (e3 = {});
  var t2 = e3.isTagged;
  void 0 === t2 && (t2 = false);
  var s2 = this.startNode();
  this.next(), s2.expressions = [];
  var i2 = this.parseTemplateElement({ isTagged: t2 });
  for (s2.quasis = [i2]; !i2.tail; )
    this.type === Ha.eof && this.raise(this.pos, "Unterminated template literal"), this.expect(Ha.dollarBraceL), s2.expressions.push(this.parseExpression()), this.expect(Ha.braceR), s2.quasis.push(i2 = this.parseTemplateElement({ isTagged: t2 }));
  return this.next(), this.finishNode(s2, "TemplateLiteral");
}, Rl.isAsyncProp = function(e3) {
  return !e3.computed && "Identifier" === e3.key.type && "async" === e3.key.name && (this.type === Ha.name || this.type === Ha.num || this.type === Ha.string || this.type === Ha.bracketL || this.type.keyword || this.options.ecmaVersion >= 9 && this.type === Ha.star) && !Ka.test(this.input.slice(this.lastTokEnd, this.start));
}, Rl.parseObj = function(e3, t2) {
  var s2 = this.startNode(), i2 = true, n2 = {};
  for (s2.properties = [], this.next(); !this.eat(Ha.braceR); ) {
    if (i2)
      i2 = false;
    else if (this.expect(Ha.comma), this.options.ecmaVersion >= 5 && this.afterTrailingComma(Ha.braceR))
      break;
    var r2 = this.parseProperty(e3, t2);
    e3 || this.checkPropClash(r2, n2, t2), s2.properties.push(r2);
  }
  return this.finishNode(s2, e3 ? "ObjectPattern" : "ObjectExpression");
}, Rl.parseProperty = function(e3, t2) {
  var s2, i2, n2, r2, o2 = this.startNode();
  if (this.options.ecmaVersion >= 9 && this.eat(Ha.ellipsis))
    return e3 ? (o2.argument = this.parseIdent(false), this.type === Ha.comma && this.raise(this.start, "Comma is not permitted after the rest element"), this.finishNode(o2, "RestElement")) : (o2.argument = this.parseMaybeAssign(false, t2), this.type === Ha.comma && t2 && t2.trailingComma < 0 && (t2.trailingComma = this.start), this.finishNode(o2, "SpreadElement"));
  this.options.ecmaVersion >= 6 && (o2.method = false, o2.shorthand = false, (e3 || t2) && (n2 = this.start, r2 = this.startLoc), e3 || (s2 = this.eat(Ha.star)));
  var a2 = this.containsEsc;
  return this.parsePropertyName(o2), !e3 && !a2 && this.options.ecmaVersion >= 8 && !s2 && this.isAsyncProp(o2) ? (i2 = true, s2 = this.options.ecmaVersion >= 9 && this.eat(Ha.star), this.parsePropertyName(o2, t2)) : i2 = false, this.parsePropertyValue(o2, e3, s2, i2, n2, r2, t2, a2), this.finishNode(o2, "Property");
}, Rl.parsePropertyValue = function(e3, t2, s2, i2, n2, r2, o2, a2) {
  if ((s2 || i2) && this.type === Ha.colon && this.unexpected(), this.eat(Ha.colon))
    e3.value = t2 ? this.parseMaybeDefault(this.start, this.startLoc) : this.parseMaybeAssign(false, o2), e3.kind = "init";
  else if (this.options.ecmaVersion >= 6 && this.type === Ha.parenL)
    t2 && this.unexpected(), e3.kind = "init", e3.method = true, e3.value = this.parseMethod(s2, i2);
  else if (t2 || a2 || !(this.options.ecmaVersion >= 5) || e3.computed || "Identifier" !== e3.key.type || "get" !== e3.key.name && "set" !== e3.key.name || this.type === Ha.comma || this.type === Ha.braceR || this.type === Ha.eq)
    this.options.ecmaVersion >= 6 && !e3.computed && "Identifier" === e3.key.type ? ((s2 || i2) && this.unexpected(), this.checkUnreserved(e3.key), "await" !== e3.key.name || this.awaitIdentPos || (this.awaitIdentPos = n2), e3.kind = "init", t2 ? e3.value = this.parseMaybeDefault(n2, r2, this.copyNode(e3.key)) : this.type === Ha.eq && o2 ? (o2.shorthandAssign < 0 && (o2.shorthandAssign = this.start), e3.value = this.parseMaybeDefault(n2, r2, this.copyNode(e3.key))) : e3.value = this.copyNode(e3.key), e3.shorthand = true) : this.unexpected();
  else {
    (s2 || i2) && this.unexpected(), e3.kind = e3.key.name, this.parsePropertyName(e3), e3.value = this.parseMethod(false);
    var l2 = "get" === e3.kind ? 0 : 1;
    if (e3.value.params.length !== l2) {
      var h2 = e3.value.start;
      "get" === e3.kind ? this.raiseRecoverable(h2, "getter should have no params") : this.raiseRecoverable(h2, "setter should have exactly one param");
    } else
      "set" === e3.kind && "RestElement" === e3.value.params[0].type && this.raiseRecoverable(e3.value.params[0].start, "Setter cannot use rest params");
  }
}, Rl.parsePropertyName = function(e3) {
  if (this.options.ecmaVersion >= 6) {
    if (this.eat(Ha.bracketL))
      return e3.computed = true, e3.key = this.parseMaybeAssign(), this.expect(Ha.bracketR), e3.key;
    e3.computed = false;
  }
  return e3.key = this.type === Ha.num || this.type === Ha.string ? this.parseExprAtom() : this.parseIdent("never" !== this.options.allowReserved);
}, Rl.initFunction = function(e3) {
  e3.id = null, this.options.ecmaVersion >= 6 && (e3.generator = e3.expression = false), this.options.ecmaVersion >= 8 && (e3.async = false);
}, Rl.parseMethod = function(e3, t2, s2) {
  var i2 = this.startNode(), n2 = this.yieldPos, r2 = this.awaitPos, o2 = this.awaitIdentPos;
  return this.initFunction(i2), this.options.ecmaVersion >= 6 && (i2.generator = e3), this.options.ecmaVersion >= 8 && (i2.async = !!t2), this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, this.enterScope(64 | ml(t2, i2.generator) | (s2 ? 128 : 0)), this.expect(Ha.parenL), i2.params = this.parseBindingList(Ha.parenR, false, this.options.ecmaVersion >= 8), this.checkYieldAwaitInDefaultParams(), this.parseFunctionBody(i2, false, true, false), this.yieldPos = n2, this.awaitPos = r2, this.awaitIdentPos = o2, this.finishNode(i2, "FunctionExpression");
}, Rl.parseArrowExpression = function(e3, t2, s2, i2) {
  var n2 = this.yieldPos, r2 = this.awaitPos, o2 = this.awaitIdentPos;
  return this.enterScope(16 | ml(s2, false)), this.initFunction(e3), this.options.ecmaVersion >= 8 && (e3.async = !!s2), this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, e3.params = this.toAssignableList(t2, true), this.parseFunctionBody(e3, true, false, i2), this.yieldPos = n2, this.awaitPos = r2, this.awaitIdentPos = o2, this.finishNode(e3, "ArrowFunctionExpression");
}, Rl.parseFunctionBody = function(e3, t2, s2, i2) {
  var n2 = t2 && this.type !== Ha.braceL, r2 = this.strict, o2 = false;
  if (n2)
    e3.body = this.parseMaybeAssign(i2), e3.expression = true, this.checkParams(e3, false);
  else {
    var a2 = this.options.ecmaVersion >= 7 && !this.isSimpleParamList(e3.params);
    r2 && !a2 || (o2 = this.strictDirective(this.end)) && a2 && this.raiseRecoverable(e3.start, "Illegal 'use strict' directive in function with non-simple parameter list");
    var l2 = this.labels;
    this.labels = [], o2 && (this.strict = true), this.checkParams(e3, !r2 && !o2 && !t2 && !s2 && this.isSimpleParamList(e3.params)), this.strict && e3.id && this.checkLValSimple(e3.id, 5), e3.body = this.parseBlock(false, void 0, o2 && !r2), e3.expression = false, this.adaptDirectivePrologue(e3.body.body), this.labels = l2;
  }
  this.exitScope();
}, Rl.isSimpleParamList = function(e3) {
  for (var t2 = 0, s2 = e3; t2 < s2.length; t2 += 1) {
    if ("Identifier" !== s2[t2].type)
      return false;
  }
  return true;
}, Rl.checkParams = function(e3, t2) {
  for (var s2 = /* @__PURE__ */ Object.create(null), i2 = 0, n2 = e3.params; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    this.checkLValInnerPattern(r2, 1, t2 ? null : s2);
  }
}, Rl.parseExprList = function(e3, t2, s2, i2) {
  for (var n2 = [], r2 = true; !this.eat(e3); ) {
    if (r2)
      r2 = false;
    else if (this.expect(Ha.comma), t2 && this.afterTrailingComma(e3))
      break;
    var o2 = void 0;
    s2 && this.type === Ha.comma ? o2 = null : this.type === Ha.ellipsis ? (o2 = this.parseSpread(i2), i2 && this.type === Ha.comma && i2.trailingComma < 0 && (i2.trailingComma = this.start)) : o2 = this.parseMaybeAssign(false, i2), n2.push(o2);
  }
  return n2;
}, Rl.checkUnreserved = function(e3) {
  var t2 = e3.start, s2 = e3.end, i2 = e3.name;
  (this.inGenerator && "yield" === i2 && this.raiseRecoverable(t2, "Cannot use 'yield' as identifier inside a generator"), this.inAsync && "await" === i2 && this.raiseRecoverable(t2, "Cannot use 'await' as identifier inside an async function"), this.currentThisScope().inClassFieldInit && "arguments" === i2 && this.raiseRecoverable(t2, "Cannot use 'arguments' in class field initializer"), !this.inClassStaticBlock || "arguments" !== i2 && "await" !== i2 || this.raise(t2, "Cannot use " + i2 + " in class static initialization block"), this.keywords.test(i2) && this.raise(t2, "Unexpected keyword '" + i2 + "'"), this.options.ecmaVersion < 6 && -1 !== this.input.slice(t2, s2).indexOf("\\")) || (this.strict ? this.reservedWordsStrict : this.reservedWords).test(i2) && (this.inAsync || "await" !== i2 || this.raiseRecoverable(t2, "Cannot use keyword 'await' outside an async function"), this.raiseRecoverable(t2, "The keyword '" + i2 + "' is reserved"));
}, Rl.parseIdent = function(e3, t2) {
  var s2 = this.startNode();
  return this.type === Ha.name ? s2.name = this.value : this.type.keyword ? (s2.name = this.type.keyword, "class" !== s2.name && "function" !== s2.name || this.lastTokEnd === this.lastTokStart + 1 && 46 === this.input.charCodeAt(this.lastTokStart) || this.context.pop()) : this.unexpected(), this.next(!!e3), this.finishNode(s2, "Identifier"), e3 || (this.checkUnreserved(s2), "await" !== s2.name || this.awaitIdentPos || (this.awaitIdentPos = s2.start)), s2;
}, Rl.parsePrivateIdent = function() {
  var e3 = this.startNode();
  return this.type === Ha.privateId ? e3.name = this.value : this.unexpected(), this.next(), this.finishNode(e3, "PrivateIdentifier"), 0 === this.privateNameStack.length ? this.raise(e3.start, "Private field '#" + e3.name + "' must be declared in an enclosing class") : this.privateNameStack[this.privateNameStack.length - 1].used.push(e3), e3;
}, Rl.parseYield = function(e3) {
  this.yieldPos || (this.yieldPos = this.start);
  var t2 = this.startNode();
  return this.next(), this.type === Ha.semi || this.canInsertSemicolon() || this.type !== Ha.star && !this.type.startsExpr ? (t2.delegate = false, t2.argument = null) : (t2.delegate = this.eat(Ha.star), t2.argument = this.parseMaybeAssign(e3)), this.finishNode(t2, "YieldExpression");
}, Rl.parseAwait = function(e3) {
  this.awaitPos || (this.awaitPos = this.start);
  var t2 = this.startNode();
  return this.next(), t2.argument = this.parseMaybeUnary(null, true, false, e3), this.finishNode(t2, "AwaitExpression");
};
var Dl = gl.prototype;
Dl.raise = function(e3, t2) {
  var s2 = cl(this.input, e3);
  t2 += " (" + s2.line + ":" + s2.column + ")";
  var i2 = new SyntaxError(t2);
  throw i2.pos = e3, i2.loc = s2, i2.raisedAt = this.pos, i2;
}, Dl.raiseRecoverable = Dl.raise, Dl.curPosition = function() {
  if (this.options.locations)
    return new ll(this.curLine, this.pos - this.lineStart);
};
var Ll = gl.prototype, Vl = function(e3) {
  this.flags = e3, this.var = [], this.lexical = [], this.functions = [], this.inClassFieldInit = false;
};
Ll.enterScope = function(e3) {
  this.scopeStack.push(new Vl(e3));
}, Ll.exitScope = function() {
  this.scopeStack.pop();
}, Ll.treatFunctionsAsVarInScope = function(e3) {
  return 2 & e3.flags || !this.inModule && 1 & e3.flags;
}, Ll.declareName = function(e3, t2, s2) {
  var i2 = false;
  if (2 === t2) {
    var n2 = this.currentScope();
    i2 = n2.lexical.indexOf(e3) > -1 || n2.functions.indexOf(e3) > -1 || n2.var.indexOf(e3) > -1, n2.lexical.push(e3), this.inModule && 1 & n2.flags && delete this.undefinedExports[e3];
  } else if (4 === t2) {
    this.currentScope().lexical.push(e3);
  } else if (3 === t2) {
    var r2 = this.currentScope();
    i2 = this.treatFunctionsAsVar ? r2.lexical.indexOf(e3) > -1 : r2.lexical.indexOf(e3) > -1 || r2.var.indexOf(e3) > -1, r2.functions.push(e3);
  } else
    for (var o2 = this.scopeStack.length - 1; o2 >= 0; --o2) {
      var a2 = this.scopeStack[o2];
      if (a2.lexical.indexOf(e3) > -1 && !(32 & a2.flags && a2.lexical[0] === e3) || !this.treatFunctionsAsVarInScope(a2) && a2.functions.indexOf(e3) > -1) {
        i2 = true;
        break;
      }
      if (a2.var.push(e3), this.inModule && 1 & a2.flags && delete this.undefinedExports[e3], 259 & a2.flags)
        break;
    }
  i2 && this.raiseRecoverable(s2, "Identifier '" + e3 + "' has already been declared");
}, Ll.checkLocalExport = function(e3) {
  -1 === this.scopeStack[0].lexical.indexOf(e3.name) && -1 === this.scopeStack[0].var.indexOf(e3.name) && (this.undefinedExports[e3.name] = e3);
}, Ll.currentScope = function() {
  return this.scopeStack[this.scopeStack.length - 1];
}, Ll.currentVarScope = function() {
  for (var e3 = this.scopeStack.length - 1; ; e3--) {
    var t2 = this.scopeStack[e3];
    if (259 & t2.flags)
      return t2;
  }
}, Ll.currentThisScope = function() {
  for (var e3 = this.scopeStack.length - 1; ; e3--) {
    var t2 = this.scopeStack[e3];
    if (259 & t2.flags && !(16 & t2.flags))
      return t2;
  }
};
var Bl = function(e3, t2, s2) {
  this.type = "", this.start = t2, this.end = 0, e3.options.locations && (this.loc = new hl(e3, s2)), e3.options.directSourceFile && (this.sourceFile = e3.options.directSourceFile), e3.options.ranges && (this.range = [t2, 0]);
}, Fl = gl.prototype;
function zl(e3, t2, s2, i2) {
  return e3.type = t2, e3.end = s2, this.options.locations && (e3.loc.end = i2), this.options.ranges && (e3.range[1] = s2), e3;
}
Fl.startNode = function() {
  return new Bl(this, this.start, this.startLoc);
}, Fl.startNodeAt = function(e3, t2) {
  return new Bl(this, e3, t2);
}, Fl.finishNode = function(e3, t2) {
  return zl.call(this, e3, t2, this.lastTokEnd, this.lastTokEndLoc);
}, Fl.finishNodeAt = function(e3, t2, s2, i2) {
  return zl.call(this, e3, t2, s2, i2);
}, Fl.copyNode = function(e3) {
  var t2 = new Bl(this, e3.start, this.startLoc);
  for (var s2 in e3)
    t2[s2] = e3[s2];
  return t2;
};
var jl = "ASCII ASCII_Hex_Digit AHex Alphabetic Alpha Any Assigned Bidi_Control Bidi_C Bidi_Mirrored Bidi_M Case_Ignorable CI Cased Changes_When_Casefolded CWCF Changes_When_Casemapped CWCM Changes_When_Lowercased CWL Changes_When_NFKC_Casefolded CWKCF Changes_When_Titlecased CWT Changes_When_Uppercased CWU Dash Default_Ignorable_Code_Point DI Deprecated Dep Diacritic Dia Emoji Emoji_Component Emoji_Modifier Emoji_Modifier_Base Emoji_Presentation Extender Ext Grapheme_Base Gr_Base Grapheme_Extend Gr_Ext Hex_Digit Hex IDS_Binary_Operator IDSB IDS_Trinary_Operator IDST ID_Continue IDC ID_Start IDS Ideographic Ideo Join_Control Join_C Logical_Order_Exception LOE Lowercase Lower Math Noncharacter_Code_Point NChar Pattern_Syntax Pat_Syn Pattern_White_Space Pat_WS Quotation_Mark QMark Radical Regional_Indicator RI Sentence_Terminal STerm Soft_Dotted SD Terminal_Punctuation Term Unified_Ideograph UIdeo Uppercase Upper Variation_Selector VS White_Space space XID_Continue XIDC XID_Start XIDS", Ul = jl + " Extended_Pictographic", Gl = Ul + " EBase EComp EMod EPres ExtPict", Wl = { 9: jl, 10: Ul, 11: Ul, 12: Gl, 13: Gl }, ql = "Cased_Letter LC Close_Punctuation Pe Connector_Punctuation Pc Control Cc cntrl Currency_Symbol Sc Dash_Punctuation Pd Decimal_Number Nd digit Enclosing_Mark Me Final_Punctuation Pf Format Cf Initial_Punctuation Pi Letter L Letter_Number Nl Line_Separator Zl Lowercase_Letter Ll Mark M Combining_Mark Math_Symbol Sm Modifier_Letter Lm Modifier_Symbol Sk Nonspacing_Mark Mn Number N Open_Punctuation Ps Other C Other_Letter Lo Other_Number No Other_Punctuation Po Other_Symbol So Paragraph_Separator Zp Private_Use Co Punctuation P punct Separator Z Space_Separator Zs Spacing_Mark Mc Surrogate Cs Symbol S Titlecase_Letter Lt Unassigned Cn Uppercase_Letter Lu", Hl = "Adlam Adlm Ahom Anatolian_Hieroglyphs Hluw Arabic Arab Armenian Armn Avestan Avst Balinese Bali Bamum Bamu Bassa_Vah Bass Batak Batk Bengali Beng Bhaiksuki Bhks Bopomofo Bopo Brahmi Brah Braille Brai Buginese Bugi Buhid Buhd Canadian_Aboriginal Cans Carian Cari Caucasian_Albanian Aghb Chakma Cakm Cham Cham Cherokee Cher Common Zyyy Coptic Copt Qaac Cuneiform Xsux Cypriot Cprt Cyrillic Cyrl Deseret Dsrt Devanagari Deva Duployan Dupl Egyptian_Hieroglyphs Egyp Elbasan Elba Ethiopic Ethi Georgian Geor Glagolitic Glag Gothic Goth Grantha Gran Greek Grek Gujarati Gujr Gurmukhi Guru Han Hani Hangul Hang Hanunoo Hano Hatran Hatr Hebrew Hebr Hiragana Hira Imperial_Aramaic Armi Inherited Zinh Qaai Inscriptional_Pahlavi Phli Inscriptional_Parthian Prti Javanese Java Kaithi Kthi Kannada Knda Katakana Kana Kayah_Li Kali Kharoshthi Khar Khmer Khmr Khojki Khoj Khudawadi Sind Lao Laoo Latin Latn Lepcha Lepc Limbu Limb Linear_A Lina Linear_B Linb Lisu Lisu Lycian Lyci Lydian Lydi Mahajani Mahj Malayalam Mlym Mandaic Mand Manichaean Mani Marchen Marc Masaram_Gondi Gonm Meetei_Mayek Mtei Mende_Kikakui Mend Meroitic_Cursive Merc Meroitic_Hieroglyphs Mero Miao Plrd Modi Mongolian Mong Mro Mroo Multani Mult Myanmar Mymr Nabataean Nbat New_Tai_Lue Talu Newa Newa Nko Nkoo Nushu Nshu Ogham Ogam Ol_Chiki Olck Old_Hungarian Hung Old_Italic Ital Old_North_Arabian Narb Old_Permic Perm Old_Persian Xpeo Old_South_Arabian Sarb Old_Turkic Orkh Oriya Orya Osage Osge Osmanya Osma Pahawh_Hmong Hmng Palmyrene Palm Pau_Cin_Hau Pauc Phags_Pa Phag Phoenician Phnx Psalter_Pahlavi Phlp Rejang Rjng Runic Runr Samaritan Samr Saurashtra Saur Sharada Shrd Shavian Shaw Siddham Sidd SignWriting Sgnw Sinhala Sinh Sora_Sompeng Sora Soyombo Soyo Sundanese Sund Syloti_Nagri Sylo Syriac Syrc Tagalog Tglg Tagbanwa Tagb Tai_Le Tale Tai_Tham Lana Tai_Viet Tavt Takri Takr Tamil Taml Tangut Tang Telugu Telu Thaana Thaa Thai Thai Tibetan Tibt Tifinagh Tfng Tirhuta Tirh Ugaritic Ugar Vai Vaii Warang_Citi Wara Yi Yiii Zanabazar_Square Zanb", Kl = Hl + " Dogra Dogr Gunjala_Gondi Gong Hanifi_Rohingya Rohg Makasar Maka Medefaidrin Medf Old_Sogdian Sogo Sogdian Sogd", Yl = Kl + " Elymaic Elym Nandinagari Nand Nyiakeng_Puachue_Hmong Hmnp Wancho Wcho", Xl = Yl + " Chorasmian Chrs Diak Dives_Akuru Khitan_Small_Script Kits Yezi Yezidi", Ql = { 9: Hl, 10: Kl, 11: Yl, 12: Xl, 13: Xl + " Cypro_Minoan Cpmn Old_Uyghur Ougr Tangsa Tnsa Toto Vithkuqi Vith" }, Jl = {};
function Zl(e3) {
  var t2 = Jl[e3] = { binary: rl(Wl[e3] + " " + ql), nonBinary: { General_Category: rl(ql), Script: rl(Ql[e3]) } };
  t2.nonBinary.Script_Extensions = t2.nonBinary.Script, t2.nonBinary.gc = t2.nonBinary.General_Category, t2.nonBinary.sc = t2.nonBinary.Script, t2.nonBinary.scx = t2.nonBinary.Script_Extensions;
}
for (var eh = 0, th = [9, 10, 11, 12, 13]; eh < th.length; eh += 1) {
  Zl(th[eh]);
}
var sh = gl.prototype, ih = function(e3) {
  this.parser = e3, this.validFlags = "gim" + (e3.options.ecmaVersion >= 6 ? "uy" : "") + (e3.options.ecmaVersion >= 9 ? "s" : "") + (e3.options.ecmaVersion >= 13 ? "d" : ""), this.unicodeProperties = Jl[e3.options.ecmaVersion >= 13 ? 13 : e3.options.ecmaVersion], this.source = "", this.flags = "", this.start = 0, this.switchU = false, this.switchN = false, this.pos = 0, this.lastIntValue = 0, this.lastStringValue = "", this.lastAssertionIsQuantifiable = false, this.numCapturingParens = 0, this.maxBackReference = 0, this.groupNames = [], this.backReferenceNames = [];
};
function nh(e3) {
  return 36 === e3 || e3 >= 40 && e3 <= 43 || 46 === e3 || 63 === e3 || e3 >= 91 && e3 <= 94 || e3 >= 123 && e3 <= 125;
}
function rh(e3) {
  return e3 >= 65 && e3 <= 90 || e3 >= 97 && e3 <= 122;
}
function oh(e3) {
  return rh(e3) || 95 === e3;
}
function ah(e3) {
  return oh(e3) || lh(e3);
}
function lh(e3) {
  return e3 >= 48 && e3 <= 57;
}
function hh(e3) {
  return e3 >= 48 && e3 <= 57 || e3 >= 65 && e3 <= 70 || e3 >= 97 && e3 <= 102;
}
function ch(e3) {
  return e3 >= 65 && e3 <= 70 ? e3 - 65 + 10 : e3 >= 97 && e3 <= 102 ? e3 - 97 + 10 : e3 - 48;
}
function uh(e3) {
  return e3 >= 48 && e3 <= 55;
}
ih.prototype.reset = function(e3, t2, s2) {
  var i2 = -1 !== s2.indexOf("u");
  this.start = 0 | e3, this.source = t2 + "", this.flags = s2, this.switchU = i2 && this.parser.options.ecmaVersion >= 6, this.switchN = i2 && this.parser.options.ecmaVersion >= 9;
}, ih.prototype.raise = function(e3) {
  this.parser.raiseRecoverable(this.start, "Invalid regular expression: /" + this.source + "/: " + e3);
}, ih.prototype.at = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  var s2 = this.source, i2 = s2.length;
  if (e3 >= i2)
    return -1;
  var n2 = s2.charCodeAt(e3);
  if (!t2 && !this.switchU || n2 <= 55295 || n2 >= 57344 || e3 + 1 >= i2)
    return n2;
  var r2 = s2.charCodeAt(e3 + 1);
  return r2 >= 56320 && r2 <= 57343 ? (n2 << 10) + r2 - 56613888 : n2;
}, ih.prototype.nextIndex = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  var s2 = this.source, i2 = s2.length;
  if (e3 >= i2)
    return i2;
  var n2, r2 = s2.charCodeAt(e3);
  return !t2 && !this.switchU || r2 <= 55295 || r2 >= 57344 || e3 + 1 >= i2 || (n2 = s2.charCodeAt(e3 + 1)) < 56320 || n2 > 57343 ? e3 + 1 : e3 + 2;
}, ih.prototype.current = function(e3) {
  return void 0 === e3 && (e3 = false), this.at(this.pos, e3);
}, ih.prototype.lookahead = function(e3) {
  return void 0 === e3 && (e3 = false), this.at(this.nextIndex(this.pos, e3), e3);
}, ih.prototype.advance = function(e3) {
  void 0 === e3 && (e3 = false), this.pos = this.nextIndex(this.pos, e3);
}, ih.prototype.eat = function(e3, t2) {
  return void 0 === t2 && (t2 = false), this.current(t2) === e3 && (this.advance(t2), true);
}, sh.validateRegExpFlags = function(e3) {
  for (var t2 = e3.validFlags, s2 = e3.flags, i2 = 0; i2 < s2.length; i2++) {
    var n2 = s2.charAt(i2);
    -1 === t2.indexOf(n2) && this.raise(e3.start, "Invalid regular expression flag"), s2.indexOf(n2, i2 + 1) > -1 && this.raise(e3.start, "Duplicate regular expression flag");
  }
}, sh.validateRegExpPattern = function(e3) {
  this.regexp_pattern(e3), !e3.switchN && this.options.ecmaVersion >= 9 && e3.groupNames.length > 0 && (e3.switchN = true, this.regexp_pattern(e3));
}, sh.regexp_pattern = function(e3) {
  e3.pos = 0, e3.lastIntValue = 0, e3.lastStringValue = "", e3.lastAssertionIsQuantifiable = false, e3.numCapturingParens = 0, e3.maxBackReference = 0, e3.groupNames.length = 0, e3.backReferenceNames.length = 0, this.regexp_disjunction(e3), e3.pos !== e3.source.length && (e3.eat(41) && e3.raise("Unmatched ')'"), (e3.eat(93) || e3.eat(125)) && e3.raise("Lone quantifier brackets")), e3.maxBackReference > e3.numCapturingParens && e3.raise("Invalid escape");
  for (var t2 = 0, s2 = e3.backReferenceNames; t2 < s2.length; t2 += 1) {
    var i2 = s2[t2];
    -1 === e3.groupNames.indexOf(i2) && e3.raise("Invalid named capture referenced");
  }
}, sh.regexp_disjunction = function(e3) {
  for (this.regexp_alternative(e3); e3.eat(124); )
    this.regexp_alternative(e3);
  this.regexp_eatQuantifier(e3, true) && e3.raise("Nothing to repeat"), e3.eat(123) && e3.raise("Lone quantifier brackets");
}, sh.regexp_alternative = function(e3) {
  for (; e3.pos < e3.source.length && this.regexp_eatTerm(e3); )
    ;
}, sh.regexp_eatTerm = function(e3) {
  return this.regexp_eatAssertion(e3) ? (e3.lastAssertionIsQuantifiable && this.regexp_eatQuantifier(e3) && e3.switchU && e3.raise("Invalid quantifier"), true) : !!(e3.switchU ? this.regexp_eatAtom(e3) : this.regexp_eatExtendedAtom(e3)) && (this.regexp_eatQuantifier(e3), true);
}, sh.regexp_eatAssertion = function(e3) {
  var t2 = e3.pos;
  if (e3.lastAssertionIsQuantifiable = false, e3.eat(94) || e3.eat(36))
    return true;
  if (e3.eat(92)) {
    if (e3.eat(66) || e3.eat(98))
      return true;
    e3.pos = t2;
  }
  if (e3.eat(40) && e3.eat(63)) {
    var s2 = false;
    if (this.options.ecmaVersion >= 9 && (s2 = e3.eat(60)), e3.eat(61) || e3.eat(33))
      return this.regexp_disjunction(e3), e3.eat(41) || e3.raise("Unterminated group"), e3.lastAssertionIsQuantifiable = !s2, true;
  }
  return e3.pos = t2, false;
}, sh.regexp_eatQuantifier = function(e3, t2) {
  return void 0 === t2 && (t2 = false), !!this.regexp_eatQuantifierPrefix(e3, t2) && (e3.eat(63), true);
}, sh.regexp_eatQuantifierPrefix = function(e3, t2) {
  return e3.eat(42) || e3.eat(43) || e3.eat(63) || this.regexp_eatBracedQuantifier(e3, t2);
}, sh.regexp_eatBracedQuantifier = function(e3, t2) {
  var s2 = e3.pos;
  if (e3.eat(123)) {
    var i2 = 0, n2 = -1;
    if (this.regexp_eatDecimalDigits(e3) && (i2 = e3.lastIntValue, e3.eat(44) && this.regexp_eatDecimalDigits(e3) && (n2 = e3.lastIntValue), e3.eat(125)))
      return -1 !== n2 && n2 < i2 && !t2 && e3.raise("numbers out of order in {} quantifier"), true;
    e3.switchU && !t2 && e3.raise("Incomplete quantifier"), e3.pos = s2;
  }
  return false;
}, sh.regexp_eatAtom = function(e3) {
  return this.regexp_eatPatternCharacters(e3) || e3.eat(46) || this.regexp_eatReverseSolidusAtomEscape(e3) || this.regexp_eatCharacterClass(e3) || this.regexp_eatUncapturingGroup(e3) || this.regexp_eatCapturingGroup(e3);
}, sh.regexp_eatReverseSolidusAtomEscape = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(92)) {
    if (this.regexp_eatAtomEscape(e3))
      return true;
    e3.pos = t2;
  }
  return false;
}, sh.regexp_eatUncapturingGroup = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(40)) {
    if (e3.eat(63) && e3.eat(58)) {
      if (this.regexp_disjunction(e3), e3.eat(41))
        return true;
      e3.raise("Unterminated group");
    }
    e3.pos = t2;
  }
  return false;
}, sh.regexp_eatCapturingGroup = function(e3) {
  if (e3.eat(40)) {
    if (this.options.ecmaVersion >= 9 ? this.regexp_groupSpecifier(e3) : 63 === e3.current() && e3.raise("Invalid group"), this.regexp_disjunction(e3), e3.eat(41))
      return e3.numCapturingParens += 1, true;
    e3.raise("Unterminated group");
  }
  return false;
}, sh.regexp_eatExtendedAtom = function(e3) {
  return e3.eat(46) || this.regexp_eatReverseSolidusAtomEscape(e3) || this.regexp_eatCharacterClass(e3) || this.regexp_eatUncapturingGroup(e3) || this.regexp_eatCapturingGroup(e3) || this.regexp_eatInvalidBracedQuantifier(e3) || this.regexp_eatExtendedPatternCharacter(e3);
}, sh.regexp_eatInvalidBracedQuantifier = function(e3) {
  return this.regexp_eatBracedQuantifier(e3, true) && e3.raise("Nothing to repeat"), false;
}, sh.regexp_eatSyntaxCharacter = function(e3) {
  var t2 = e3.current();
  return !!nh(t2) && (e3.lastIntValue = t2, e3.advance(), true);
}, sh.regexp_eatPatternCharacters = function(e3) {
  for (var t2 = e3.pos, s2 = 0; -1 !== (s2 = e3.current()) && !nh(s2); )
    e3.advance();
  return e3.pos !== t2;
}, sh.regexp_eatExtendedPatternCharacter = function(e3) {
  var t2 = e3.current();
  return !(-1 === t2 || 36 === t2 || t2 >= 40 && t2 <= 43 || 46 === t2 || 63 === t2 || 91 === t2 || 94 === t2 || 124 === t2) && (e3.advance(), true);
}, sh.regexp_groupSpecifier = function(e3) {
  if (e3.eat(63)) {
    if (this.regexp_eatGroupName(e3))
      return -1 !== e3.groupNames.indexOf(e3.lastStringValue) && e3.raise("Duplicate capture group name"), void e3.groupNames.push(e3.lastStringValue);
    e3.raise("Invalid group");
  }
}, sh.regexp_eatGroupName = function(e3) {
  if (e3.lastStringValue = "", e3.eat(60)) {
    if (this.regexp_eatRegExpIdentifierName(e3) && e3.eat(62))
      return true;
    e3.raise("Invalid capture group name");
  }
  return false;
}, sh.regexp_eatRegExpIdentifierName = function(e3) {
  if (e3.lastStringValue = "", this.regexp_eatRegExpIdentifierStart(e3)) {
    for (e3.lastStringValue += ol(e3.lastIntValue); this.regexp_eatRegExpIdentifierPart(e3); )
      e3.lastStringValue += ol(e3.lastIntValue);
    return true;
  }
  return false;
}, sh.regexp_eatRegExpIdentifierStart = function(e3) {
  var t2 = e3.pos, s2 = this.options.ecmaVersion >= 11, i2 = e3.current(s2);
  return e3.advance(s2), 92 === i2 && this.regexp_eatRegExpUnicodeEscapeSequence(e3, s2) && (i2 = e3.lastIntValue), function(e4) {
    return Ba(e4, true) || 36 === e4 || 95 === e4;
  }(i2) ? (e3.lastIntValue = i2, true) : (e3.pos = t2, false);
}, sh.regexp_eatRegExpIdentifierPart = function(e3) {
  var t2 = e3.pos, s2 = this.options.ecmaVersion >= 11, i2 = e3.current(s2);
  return e3.advance(s2), 92 === i2 && this.regexp_eatRegExpUnicodeEscapeSequence(e3, s2) && (i2 = e3.lastIntValue), function(e4) {
    return Fa(e4, true) || 36 === e4 || 95 === e4 || 8204 === e4 || 8205 === e4;
  }(i2) ? (e3.lastIntValue = i2, true) : (e3.pos = t2, false);
}, sh.regexp_eatAtomEscape = function(e3) {
  return !!(this.regexp_eatBackReference(e3) || this.regexp_eatCharacterClassEscape(e3) || this.regexp_eatCharacterEscape(e3) || e3.switchN && this.regexp_eatKGroupName(e3)) || (e3.switchU && (99 === e3.current() && e3.raise("Invalid unicode escape"), e3.raise("Invalid escape")), false);
}, sh.regexp_eatBackReference = function(e3) {
  var t2 = e3.pos;
  if (this.regexp_eatDecimalEscape(e3)) {
    var s2 = e3.lastIntValue;
    if (e3.switchU)
      return s2 > e3.maxBackReference && (e3.maxBackReference = s2), true;
    if (s2 <= e3.numCapturingParens)
      return true;
    e3.pos = t2;
  }
  return false;
}, sh.regexp_eatKGroupName = function(e3) {
  if (e3.eat(107)) {
    if (this.regexp_eatGroupName(e3))
      return e3.backReferenceNames.push(e3.lastStringValue), true;
    e3.raise("Invalid named reference");
  }
  return false;
}, sh.regexp_eatCharacterEscape = function(e3) {
  return this.regexp_eatControlEscape(e3) || this.regexp_eatCControlLetter(e3) || this.regexp_eatZero(e3) || this.regexp_eatHexEscapeSequence(e3) || this.regexp_eatRegExpUnicodeEscapeSequence(e3, false) || !e3.switchU && this.regexp_eatLegacyOctalEscapeSequence(e3) || this.regexp_eatIdentityEscape(e3);
}, sh.regexp_eatCControlLetter = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(99)) {
    if (this.regexp_eatControlLetter(e3))
      return true;
    e3.pos = t2;
  }
  return false;
}, sh.regexp_eatZero = function(e3) {
  return 48 === e3.current() && !lh(e3.lookahead()) && (e3.lastIntValue = 0, e3.advance(), true);
}, sh.regexp_eatControlEscape = function(e3) {
  var t2 = e3.current();
  return 116 === t2 ? (e3.lastIntValue = 9, e3.advance(), true) : 110 === t2 ? (e3.lastIntValue = 10, e3.advance(), true) : 118 === t2 ? (e3.lastIntValue = 11, e3.advance(), true) : 102 === t2 ? (e3.lastIntValue = 12, e3.advance(), true) : 114 === t2 && (e3.lastIntValue = 13, e3.advance(), true);
}, sh.regexp_eatControlLetter = function(e3) {
  var t2 = e3.current();
  return !!rh(t2) && (e3.lastIntValue = t2 % 32, e3.advance(), true);
}, sh.regexp_eatRegExpUnicodeEscapeSequence = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  var s2, i2 = e3.pos, n2 = t2 || e3.switchU;
  if (e3.eat(117)) {
    if (this.regexp_eatFixedHexDigits(e3, 4)) {
      var r2 = e3.lastIntValue;
      if (n2 && r2 >= 55296 && r2 <= 56319) {
        var o2 = e3.pos;
        if (e3.eat(92) && e3.eat(117) && this.regexp_eatFixedHexDigits(e3, 4)) {
          var a2 = e3.lastIntValue;
          if (a2 >= 56320 && a2 <= 57343)
            return e3.lastIntValue = 1024 * (r2 - 55296) + (a2 - 56320) + 65536, true;
        }
        e3.pos = o2, e3.lastIntValue = r2;
      }
      return true;
    }
    if (n2 && e3.eat(123) && this.regexp_eatHexDigits(e3) && e3.eat(125) && ((s2 = e3.lastIntValue) >= 0 && s2 <= 1114111))
      return true;
    n2 && e3.raise("Invalid unicode escape"), e3.pos = i2;
  }
  return false;
}, sh.regexp_eatIdentityEscape = function(e3) {
  if (e3.switchU)
    return !!this.regexp_eatSyntaxCharacter(e3) || !!e3.eat(47) && (e3.lastIntValue = 47, true);
  var t2 = e3.current();
  return !(99 === t2 || e3.switchN && 107 === t2) && (e3.lastIntValue = t2, e3.advance(), true);
}, sh.regexp_eatDecimalEscape = function(e3) {
  e3.lastIntValue = 0;
  var t2 = e3.current();
  if (t2 >= 49 && t2 <= 57) {
    do {
      e3.lastIntValue = 10 * e3.lastIntValue + (t2 - 48), e3.advance();
    } while ((t2 = e3.current()) >= 48 && t2 <= 57);
    return true;
  }
  return false;
}, sh.regexp_eatCharacterClassEscape = function(e3) {
  var t2 = e3.current();
  if (function(e4) {
    return 100 === e4 || 68 === e4 || 115 === e4 || 83 === e4 || 119 === e4 || 87 === e4;
  }(t2))
    return e3.lastIntValue = -1, e3.advance(), true;
  if (e3.switchU && this.options.ecmaVersion >= 9 && (80 === t2 || 112 === t2)) {
    if (e3.lastIntValue = -1, e3.advance(), e3.eat(123) && this.regexp_eatUnicodePropertyValueExpression(e3) && e3.eat(125))
      return true;
    e3.raise("Invalid property name");
  }
  return false;
}, sh.regexp_eatUnicodePropertyValueExpression = function(e3) {
  var t2 = e3.pos;
  if (this.regexp_eatUnicodePropertyName(e3) && e3.eat(61)) {
    var s2 = e3.lastStringValue;
    if (this.regexp_eatUnicodePropertyValue(e3)) {
      var i2 = e3.lastStringValue;
      return this.regexp_validateUnicodePropertyNameAndValue(e3, s2, i2), true;
    }
  }
  if (e3.pos = t2, this.regexp_eatLoneUnicodePropertyNameOrValue(e3)) {
    var n2 = e3.lastStringValue;
    return this.regexp_validateUnicodePropertyNameOrValue(e3, n2), true;
  }
  return false;
}, sh.regexp_validateUnicodePropertyNameAndValue = function(e3, t2, s2) {
  il(e3.unicodeProperties.nonBinary, t2) || e3.raise("Invalid property name"), e3.unicodeProperties.nonBinary[t2].test(s2) || e3.raise("Invalid property value");
}, sh.regexp_validateUnicodePropertyNameOrValue = function(e3, t2) {
  e3.unicodeProperties.binary.test(t2) || e3.raise("Invalid property name");
}, sh.regexp_eatUnicodePropertyName = function(e3) {
  var t2 = 0;
  for (e3.lastStringValue = ""; oh(t2 = e3.current()); )
    e3.lastStringValue += ol(t2), e3.advance();
  return "" !== e3.lastStringValue;
}, sh.regexp_eatUnicodePropertyValue = function(e3) {
  var t2 = 0;
  for (e3.lastStringValue = ""; ah(t2 = e3.current()); )
    e3.lastStringValue += ol(t2), e3.advance();
  return "" !== e3.lastStringValue;
}, sh.regexp_eatLoneUnicodePropertyNameOrValue = function(e3) {
  return this.regexp_eatUnicodePropertyValue(e3);
}, sh.regexp_eatCharacterClass = function(e3) {
  if (e3.eat(91)) {
    if (e3.eat(94), this.regexp_classRanges(e3), e3.eat(93))
      return true;
    e3.raise("Unterminated character class");
  }
  return false;
}, sh.regexp_classRanges = function(e3) {
  for (; this.regexp_eatClassAtom(e3); ) {
    var t2 = e3.lastIntValue;
    if (e3.eat(45) && this.regexp_eatClassAtom(e3)) {
      var s2 = e3.lastIntValue;
      !e3.switchU || -1 !== t2 && -1 !== s2 || e3.raise("Invalid character class"), -1 !== t2 && -1 !== s2 && t2 > s2 && e3.raise("Range out of order in character class");
    }
  }
}, sh.regexp_eatClassAtom = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(92)) {
    if (this.regexp_eatClassEscape(e3))
      return true;
    if (e3.switchU) {
      var s2 = e3.current();
      (99 === s2 || uh(s2)) && e3.raise("Invalid class escape"), e3.raise("Invalid escape");
    }
    e3.pos = t2;
  }
  var i2 = e3.current();
  return 93 !== i2 && (e3.lastIntValue = i2, e3.advance(), true);
}, sh.regexp_eatClassEscape = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(98))
    return e3.lastIntValue = 8, true;
  if (e3.switchU && e3.eat(45))
    return e3.lastIntValue = 45, true;
  if (!e3.switchU && e3.eat(99)) {
    if (this.regexp_eatClassControlLetter(e3))
      return true;
    e3.pos = t2;
  }
  return this.regexp_eatCharacterClassEscape(e3) || this.regexp_eatCharacterEscape(e3);
}, sh.regexp_eatClassControlLetter = function(e3) {
  var t2 = e3.current();
  return !(!lh(t2) && 95 !== t2) && (e3.lastIntValue = t2 % 32, e3.advance(), true);
}, sh.regexp_eatHexEscapeSequence = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(120)) {
    if (this.regexp_eatFixedHexDigits(e3, 2))
      return true;
    e3.switchU && e3.raise("Invalid escape"), e3.pos = t2;
  }
  return false;
}, sh.regexp_eatDecimalDigits = function(e3) {
  var t2 = e3.pos, s2 = 0;
  for (e3.lastIntValue = 0; lh(s2 = e3.current()); )
    e3.lastIntValue = 10 * e3.lastIntValue + (s2 - 48), e3.advance();
  return e3.pos !== t2;
}, sh.regexp_eatHexDigits = function(e3) {
  var t2 = e3.pos, s2 = 0;
  for (e3.lastIntValue = 0; hh(s2 = e3.current()); )
    e3.lastIntValue = 16 * e3.lastIntValue + ch(s2), e3.advance();
  return e3.pos !== t2;
}, sh.regexp_eatLegacyOctalEscapeSequence = function(e3) {
  if (this.regexp_eatOctalDigit(e3)) {
    var t2 = e3.lastIntValue;
    if (this.regexp_eatOctalDigit(e3)) {
      var s2 = e3.lastIntValue;
      t2 <= 3 && this.regexp_eatOctalDigit(e3) ? e3.lastIntValue = 64 * t2 + 8 * s2 + e3.lastIntValue : e3.lastIntValue = 8 * t2 + s2;
    } else
      e3.lastIntValue = t2;
    return true;
  }
  return false;
}, sh.regexp_eatOctalDigit = function(e3) {
  var t2 = e3.current();
  return uh(t2) ? (e3.lastIntValue = t2 - 48, e3.advance(), true) : (e3.lastIntValue = 0, false);
}, sh.regexp_eatFixedHexDigits = function(e3, t2) {
  var s2 = e3.pos;
  e3.lastIntValue = 0;
  for (var i2 = 0; i2 < t2; ++i2) {
    var n2 = e3.current();
    if (!hh(n2))
      return e3.pos = s2, false;
    e3.lastIntValue = 16 * e3.lastIntValue + ch(n2), e3.advance();
  }
  return true;
};
var dh = function(e3) {
  this.type = e3.type, this.value = e3.value, this.start = e3.start, this.end = e3.end, e3.options.locations && (this.loc = new hl(e3, e3.startLoc, e3.endLoc)), e3.options.ranges && (this.range = [e3.start, e3.end]);
}, ph = gl.prototype;
function fh(e3) {
  return "function" != typeof BigInt ? null : BigInt(e3.replace(/_/g, ""));
}
ph.next = function(e3) {
  !e3 && this.type.keyword && this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword " + this.type.keyword), this.options.onToken && this.options.onToken(new dh(this)), this.lastTokEnd = this.end, this.lastTokStart = this.start, this.lastTokEndLoc = this.endLoc, this.lastTokStartLoc = this.startLoc, this.nextToken();
}, ph.getToken = function() {
  return this.next(), new dh(this);
}, "undefined" != typeof Symbol && (ph[Symbol.iterator] = function() {
  var e3 = this;
  return { next: function() {
    var t2 = e3.getToken();
    return { done: t2.type === Ha.eof, value: t2 };
  } };
}), ph.nextToken = function() {
  var e3 = this.curContext();
  return e3 && e3.preserveSpace || this.skipSpace(), this.start = this.pos, this.options.locations && (this.startLoc = this.curPosition()), this.pos >= this.input.length ? this.finishToken(Ha.eof) : e3.override ? e3.override(this) : void this.readToken(this.fullCharCodeAtPos());
}, ph.readToken = function(e3) {
  return Ba(e3, this.options.ecmaVersion >= 6) || 92 === e3 ? this.readWord() : this.getTokenFromCode(e3);
}, ph.fullCharCodeAtPos = function() {
  var e3 = this.input.charCodeAt(this.pos);
  if (e3 <= 55295 || e3 >= 56320)
    return e3;
  var t2 = this.input.charCodeAt(this.pos + 1);
  return t2 <= 56319 || t2 >= 57344 ? e3 : (e3 << 10) + t2 - 56613888;
}, ph.skipBlockComment = function() {
  var e3 = this.options.onComment && this.curPosition(), t2 = this.pos, s2 = this.input.indexOf("*/", this.pos += 2);
  if (-1 === s2 && this.raise(this.pos - 2, "Unterminated comment"), this.pos = s2 + 2, this.options.locations)
    for (var i2 = void 0, n2 = t2; (i2 = Qa(this.input, n2, this.pos)) > -1; )
      ++this.curLine, n2 = this.lineStart = i2;
  this.options.onComment && this.options.onComment(true, this.input.slice(t2 + 2, s2), t2, this.pos, e3, this.curPosition());
}, ph.skipLineComment = function(e3) {
  for (var t2 = this.pos, s2 = this.options.onComment && this.curPosition(), i2 = this.input.charCodeAt(this.pos += e3); this.pos < this.input.length && !Xa(i2); )
    i2 = this.input.charCodeAt(++this.pos);
  this.options.onComment && this.options.onComment(false, this.input.slice(t2 + e3, this.pos), t2, this.pos, s2, this.curPosition());
}, ph.skipSpace = function() {
  e:
    for (; this.pos < this.input.length; ) {
      var e3 = this.input.charCodeAt(this.pos);
      switch (e3) {
        case 32:
        case 160:
          ++this.pos;
          break;
        case 13:
          10 === this.input.charCodeAt(this.pos + 1) && ++this.pos;
        case 10:
        case 8232:
        case 8233:
          ++this.pos, this.options.locations && (++this.curLine, this.lineStart = this.pos);
          break;
        case 47:
          switch (this.input.charCodeAt(this.pos + 1)) {
            case 42:
              this.skipBlockComment();
              break;
            case 47:
              this.skipLineComment(2);
              break;
            default:
              break e;
          }
          break;
        default:
          if (!(e3 > 8 && e3 < 14 || e3 >= 5760 && Ja.test(String.fromCharCode(e3))))
            break e;
          ++this.pos;
      }
    }
}, ph.finishToken = function(e3, t2) {
  this.end = this.pos, this.options.locations && (this.endLoc = this.curPosition());
  var s2 = this.type;
  this.type = e3, this.value = t2, this.updateContext(s2);
}, ph.readToken_dot = function() {
  var e3 = this.input.charCodeAt(this.pos + 1);
  if (e3 >= 48 && e3 <= 57)
    return this.readNumber(true);
  var t2 = this.input.charCodeAt(this.pos + 2);
  return this.options.ecmaVersion >= 6 && 46 === e3 && 46 === t2 ? (this.pos += 3, this.finishToken(Ha.ellipsis)) : (++this.pos, this.finishToken(Ha.dot));
}, ph.readToken_slash = function() {
  var e3 = this.input.charCodeAt(this.pos + 1);
  return this.exprAllowed ? (++this.pos, this.readRegexp()) : 61 === e3 ? this.finishOp(Ha.assign, 2) : this.finishOp(Ha.slash, 1);
}, ph.readToken_mult_modulo_exp = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1), s2 = 1, i2 = 42 === e3 ? Ha.star : Ha.modulo;
  return this.options.ecmaVersion >= 7 && 42 === e3 && 42 === t2 && (++s2, i2 = Ha.starstar, t2 = this.input.charCodeAt(this.pos + 2)), 61 === t2 ? this.finishOp(Ha.assign, s2 + 1) : this.finishOp(i2, s2);
}, ph.readToken_pipe_amp = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  if (t2 === e3) {
    if (this.options.ecmaVersion >= 12) {
      if (61 === this.input.charCodeAt(this.pos + 2))
        return this.finishOp(Ha.assign, 3);
    }
    return this.finishOp(124 === e3 ? Ha.logicalOR : Ha.logicalAND, 2);
  }
  return 61 === t2 ? this.finishOp(Ha.assign, 2) : this.finishOp(124 === e3 ? Ha.bitwiseOR : Ha.bitwiseAND, 1);
}, ph.readToken_caret = function() {
  return 61 === this.input.charCodeAt(this.pos + 1) ? this.finishOp(Ha.assign, 2) : this.finishOp(Ha.bitwiseXOR, 1);
}, ph.readToken_plus_min = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  return t2 === e3 ? 45 !== t2 || this.inModule || 62 !== this.input.charCodeAt(this.pos + 2) || 0 !== this.lastTokEnd && !Ka.test(this.input.slice(this.lastTokEnd, this.pos)) ? this.finishOp(Ha.incDec, 2) : (this.skipLineComment(3), this.skipSpace(), this.nextToken()) : 61 === t2 ? this.finishOp(Ha.assign, 2) : this.finishOp(Ha.plusMin, 1);
}, ph.readToken_lt_gt = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1), s2 = 1;
  return t2 === e3 ? (s2 = 62 === e3 && 62 === this.input.charCodeAt(this.pos + 2) ? 3 : 2, 61 === this.input.charCodeAt(this.pos + s2) ? this.finishOp(Ha.assign, s2 + 1) : this.finishOp(Ha.bitShift, s2)) : 33 !== t2 || 60 !== e3 || this.inModule || 45 !== this.input.charCodeAt(this.pos + 2) || 45 !== this.input.charCodeAt(this.pos + 3) ? (61 === t2 && (s2 = 2), this.finishOp(Ha.relational, s2)) : (this.skipLineComment(4), this.skipSpace(), this.nextToken());
}, ph.readToken_eq_excl = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  return 61 === t2 ? this.finishOp(Ha.equality, 61 === this.input.charCodeAt(this.pos + 2) ? 3 : 2) : 61 === e3 && 62 === t2 && this.options.ecmaVersion >= 6 ? (this.pos += 2, this.finishToken(Ha.arrow)) : this.finishOp(61 === e3 ? Ha.eq : Ha.prefix, 1);
}, ph.readToken_question = function() {
  var e3 = this.options.ecmaVersion;
  if (e3 >= 11) {
    var t2 = this.input.charCodeAt(this.pos + 1);
    if (46 === t2) {
      var s2 = this.input.charCodeAt(this.pos + 2);
      if (s2 < 48 || s2 > 57)
        return this.finishOp(Ha.questionDot, 2);
    }
    if (63 === t2) {
      if (e3 >= 12) {
        if (61 === this.input.charCodeAt(this.pos + 2))
          return this.finishOp(Ha.assign, 3);
      }
      return this.finishOp(Ha.coalesce, 2);
    }
  }
  return this.finishOp(Ha.question, 1);
}, ph.readToken_numberSign = function() {
  var e3 = 35;
  if (this.options.ecmaVersion >= 13 && (++this.pos, Ba(e3 = this.fullCharCodeAtPos(), true) || 92 === e3))
    return this.finishToken(Ha.privateId, this.readWord1());
  this.raise(this.pos, "Unexpected character '" + ol(e3) + "'");
}, ph.getTokenFromCode = function(e3) {
  switch (e3) {
    case 46:
      return this.readToken_dot();
    case 40:
      return ++this.pos, this.finishToken(Ha.parenL);
    case 41:
      return ++this.pos, this.finishToken(Ha.parenR);
    case 59:
      return ++this.pos, this.finishToken(Ha.semi);
    case 44:
      return ++this.pos, this.finishToken(Ha.comma);
    case 91:
      return ++this.pos, this.finishToken(Ha.bracketL);
    case 93:
      return ++this.pos, this.finishToken(Ha.bracketR);
    case 123:
      return ++this.pos, this.finishToken(Ha.braceL);
    case 125:
      return ++this.pos, this.finishToken(Ha.braceR);
    case 58:
      return ++this.pos, this.finishToken(Ha.colon);
    case 96:
      if (this.options.ecmaVersion < 6)
        break;
      return ++this.pos, this.finishToken(Ha.backQuote);
    case 48:
      var t2 = this.input.charCodeAt(this.pos + 1);
      if (120 === t2 || 88 === t2)
        return this.readRadixNumber(16);
      if (this.options.ecmaVersion >= 6) {
        if (111 === t2 || 79 === t2)
          return this.readRadixNumber(8);
        if (98 === t2 || 66 === t2)
          return this.readRadixNumber(2);
      }
    case 49:
    case 50:
    case 51:
    case 52:
    case 53:
    case 54:
    case 55:
    case 56:
    case 57:
      return this.readNumber(false);
    case 34:
    case 39:
      return this.readString(e3);
    case 47:
      return this.readToken_slash();
    case 37:
    case 42:
      return this.readToken_mult_modulo_exp(e3);
    case 124:
    case 38:
      return this.readToken_pipe_amp(e3);
    case 94:
      return this.readToken_caret();
    case 43:
    case 45:
      return this.readToken_plus_min(e3);
    case 60:
    case 62:
      return this.readToken_lt_gt(e3);
    case 61:
    case 33:
      return this.readToken_eq_excl(e3);
    case 63:
      return this.readToken_question();
    case 126:
      return this.finishOp(Ha.prefix, 1);
    case 35:
      return this.readToken_numberSign();
  }
  this.raise(this.pos, "Unexpected character '" + ol(e3) + "'");
}, ph.finishOp = function(e3, t2) {
  var s2 = this.input.slice(this.pos, this.pos + t2);
  return this.pos += t2, this.finishToken(e3, s2);
}, ph.readRegexp = function() {
  for (var e3, t2, s2 = this.pos; ; ) {
    this.pos >= this.input.length && this.raise(s2, "Unterminated regular expression");
    var i2 = this.input.charAt(this.pos);
    if (Ka.test(i2) && this.raise(s2, "Unterminated regular expression"), e3)
      e3 = false;
    else {
      if ("[" === i2)
        t2 = true;
      else if ("]" === i2 && t2)
        t2 = false;
      else if ("/" === i2 && !t2)
        break;
      e3 = "\\" === i2;
    }
    ++this.pos;
  }
  var n2 = this.input.slice(s2, this.pos);
  ++this.pos;
  var r2 = this.pos, o2 = this.readWord1();
  this.containsEsc && this.unexpected(r2);
  var a2 = this.regexpState || (this.regexpState = new ih(this));
  a2.reset(s2, n2, o2), this.validateRegExpFlags(a2), this.validateRegExpPattern(a2);
  var l2 = null;
  try {
    l2 = new RegExp(n2, o2);
  } catch (e4) {
  }
  return this.finishToken(Ha.regexp, { pattern: n2, flags: o2, value: l2 });
}, ph.readInt = function(e3, t2, s2) {
  for (var i2 = this.options.ecmaVersion >= 12 && void 0 === t2, n2 = s2 && 48 === this.input.charCodeAt(this.pos), r2 = this.pos, o2 = 0, a2 = 0, l2 = 0, h2 = null == t2 ? 1 / 0 : t2; l2 < h2; ++l2, ++this.pos) {
    var c2 = this.input.charCodeAt(this.pos), u2 = void 0;
    if (i2 && 95 === c2)
      n2 && this.raiseRecoverable(this.pos, "Numeric separator is not allowed in legacy octal numeric literals"), 95 === a2 && this.raiseRecoverable(this.pos, "Numeric separator must be exactly one underscore"), 0 === l2 && this.raiseRecoverable(this.pos, "Numeric separator is not allowed at the first of digits"), a2 = c2;
    else {
      if ((u2 = c2 >= 97 ? c2 - 97 + 10 : c2 >= 65 ? c2 - 65 + 10 : c2 >= 48 && c2 <= 57 ? c2 - 48 : 1 / 0) >= e3)
        break;
      a2 = c2, o2 = o2 * e3 + u2;
    }
  }
  return i2 && 95 === a2 && this.raiseRecoverable(this.pos - 1, "Numeric separator is not allowed at the last of digits"), this.pos === r2 || null != t2 && this.pos - r2 !== t2 ? null : o2;
}, ph.readRadixNumber = function(e3) {
  var t2 = this.pos;
  this.pos += 2;
  var s2 = this.readInt(e3);
  return null == s2 && this.raise(this.start + 2, "Expected number in radix " + e3), this.options.ecmaVersion >= 11 && 110 === this.input.charCodeAt(this.pos) ? (s2 = fh(this.input.slice(t2, this.pos)), ++this.pos) : Ba(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number"), this.finishToken(Ha.num, s2);
}, ph.readNumber = function(e3) {
  var t2 = this.pos;
  e3 || null !== this.readInt(10, void 0, true) || this.raise(t2, "Invalid number");
  var s2 = this.pos - t2 >= 2 && 48 === this.input.charCodeAt(t2);
  s2 && this.strict && this.raise(t2, "Invalid number");
  var i2 = this.input.charCodeAt(this.pos);
  if (!s2 && !e3 && this.options.ecmaVersion >= 11 && 110 === i2) {
    var n2 = fh(this.input.slice(t2, this.pos));
    return ++this.pos, Ba(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number"), this.finishToken(Ha.num, n2);
  }
  s2 && /[89]/.test(this.input.slice(t2, this.pos)) && (s2 = false), 46 !== i2 || s2 || (++this.pos, this.readInt(10), i2 = this.input.charCodeAt(this.pos)), 69 !== i2 && 101 !== i2 || s2 || (43 !== (i2 = this.input.charCodeAt(++this.pos)) && 45 !== i2 || ++this.pos, null === this.readInt(10) && this.raise(t2, "Invalid number")), Ba(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number");
  var r2, o2 = (r2 = this.input.slice(t2, this.pos), s2 ? parseInt(r2, 8) : parseFloat(r2.replace(/_/g, "")));
  return this.finishToken(Ha.num, o2);
}, ph.readCodePoint = function() {
  var e3;
  if (123 === this.input.charCodeAt(this.pos)) {
    this.options.ecmaVersion < 6 && this.unexpected();
    var t2 = ++this.pos;
    e3 = this.readHexChar(this.input.indexOf("}", this.pos) - this.pos), ++this.pos, e3 > 1114111 && this.invalidStringToken(t2, "Code point out of bounds");
  } else
    e3 = this.readHexChar(4);
  return e3;
}, ph.readString = function(e3) {
  for (var t2 = "", s2 = ++this.pos; ; ) {
    this.pos >= this.input.length && this.raise(this.start, "Unterminated string constant");
    var i2 = this.input.charCodeAt(this.pos);
    if (i2 === e3)
      break;
    92 === i2 ? (t2 += this.input.slice(s2, this.pos), t2 += this.readEscapedChar(false), s2 = this.pos) : 8232 === i2 || 8233 === i2 ? (this.options.ecmaVersion < 10 && this.raise(this.start, "Unterminated string constant"), ++this.pos, this.options.locations && (this.curLine++, this.lineStart = this.pos)) : (Xa(i2) && this.raise(this.start, "Unterminated string constant"), ++this.pos);
  }
  return t2 += this.input.slice(s2, this.pos++), this.finishToken(Ha.string, t2);
};
var mh = {};
ph.tryReadTemplateToken = function() {
  this.inTemplateElement = true;
  try {
    this.readTmplToken();
  } catch (e3) {
    if (e3 !== mh)
      throw e3;
    this.readInvalidTemplateToken();
  }
  this.inTemplateElement = false;
}, ph.invalidStringToken = function(e3, t2) {
  if (this.inTemplateElement && this.options.ecmaVersion >= 9)
    throw mh;
  this.raise(e3, t2);
}, ph.readTmplToken = function() {
  for (var e3 = "", t2 = this.pos; ; ) {
    this.pos >= this.input.length && this.raise(this.start, "Unterminated template");
    var s2 = this.input.charCodeAt(this.pos);
    if (96 === s2 || 36 === s2 && 123 === this.input.charCodeAt(this.pos + 1))
      return this.pos !== this.start || this.type !== Ha.template && this.type !== Ha.invalidTemplate ? (e3 += this.input.slice(t2, this.pos), this.finishToken(Ha.template, e3)) : 36 === s2 ? (this.pos += 2, this.finishToken(Ha.dollarBraceL)) : (++this.pos, this.finishToken(Ha.backQuote));
    if (92 === s2)
      e3 += this.input.slice(t2, this.pos), e3 += this.readEscapedChar(true), t2 = this.pos;
    else if (Xa(s2)) {
      switch (e3 += this.input.slice(t2, this.pos), ++this.pos, s2) {
        case 13:
          10 === this.input.charCodeAt(this.pos) && ++this.pos;
        case 10:
          e3 += "\n";
          break;
        default:
          e3 += String.fromCharCode(s2);
      }
      this.options.locations && (++this.curLine, this.lineStart = this.pos), t2 = this.pos;
    } else
      ++this.pos;
  }
}, ph.readInvalidTemplateToken = function() {
  for (; this.pos < this.input.length; this.pos++)
    switch (this.input[this.pos]) {
      case "\\":
        ++this.pos;
        break;
      case "$":
        if ("{" !== this.input[this.pos + 1])
          break;
      case "`":
        return this.finishToken(Ha.invalidTemplate, this.input.slice(this.start, this.pos));
    }
  this.raise(this.start, "Unterminated template");
}, ph.readEscapedChar = function(e3) {
  var t2 = this.input.charCodeAt(++this.pos);
  switch (++this.pos, t2) {
    case 110:
      return "\n";
    case 114:
      return "\r";
    case 120:
      return String.fromCharCode(this.readHexChar(2));
    case 117:
      return ol(this.readCodePoint());
    case 116:
      return "	";
    case 98:
      return "\b";
    case 118:
      return "\v";
    case 102:
      return "\f";
    case 13:
      10 === this.input.charCodeAt(this.pos) && ++this.pos;
    case 10:
      return this.options.locations && (this.lineStart = this.pos, ++this.curLine), "";
    case 56:
    case 57:
      if (this.strict && this.invalidStringToken(this.pos - 1, "Invalid escape sequence"), e3) {
        var s2 = this.pos - 1;
        return this.invalidStringToken(s2, "Invalid escape sequence in template string"), null;
      }
    default:
      if (t2 >= 48 && t2 <= 55) {
        var i2 = this.input.substr(this.pos - 1, 3).match(/^[0-7]+/)[0], n2 = parseInt(i2, 8);
        return n2 > 255 && (i2 = i2.slice(0, -1), n2 = parseInt(i2, 8)), this.pos += i2.length - 1, t2 = this.input.charCodeAt(this.pos), "0" === i2 && 56 !== t2 && 57 !== t2 || !this.strict && !e3 || this.invalidStringToken(this.pos - 1 - i2.length, e3 ? "Octal literal in template string" : "Octal literal in strict mode"), String.fromCharCode(n2);
      }
      return Xa(t2) ? "" : String.fromCharCode(t2);
  }
}, ph.readHexChar = function(e3) {
  var t2 = this.pos, s2 = this.readInt(16, e3);
  return null === s2 && this.invalidStringToken(t2, "Bad character escape sequence"), s2;
}, ph.readWord1 = function() {
  this.containsEsc = false;
  for (var e3 = "", t2 = true, s2 = this.pos, i2 = this.options.ecmaVersion >= 6; this.pos < this.input.length; ) {
    var n2 = this.fullCharCodeAtPos();
    if (Fa(n2, i2))
      this.pos += n2 <= 65535 ? 1 : 2;
    else {
      if (92 !== n2)
        break;
      this.containsEsc = true, e3 += this.input.slice(s2, this.pos);
      var r2 = this.pos;
      117 !== this.input.charCodeAt(++this.pos) && this.invalidStringToken(this.pos, "Expecting Unicode escape sequence \\uXXXX"), ++this.pos;
      var o2 = this.readCodePoint();
      (t2 ? Ba : Fa)(o2, i2) || this.invalidStringToken(r2, "Invalid Unicode escape"), e3 += ol(o2), s2 = this.pos;
    }
    t2 = false;
  }
  return e3 + this.input.slice(s2, this.pos);
}, ph.readWord = function() {
  var e3 = this.readWord1(), t2 = Ha.name;
  return this.keywords.test(e3) && (t2 = Wa[e3]), this.finishToken(t2, e3);
};
var gh = "8.8.1";
gl.acorn = { Parser: gl, version: gh, defaultOptions: ul, Position: ll, SourceLocation: hl, getLineInfo: cl, Node: Bl, TokenType: za, tokTypes: Ha, keywordTypes: Wa, TokContext: Nl, tokContexts: _l, isIdentifierChar: Fa, isIdentifierStart: Ba, Token: dh, isNewLine: Xa, lineBreak: Ka, lineBreakG: Ya, nonASCIIwhitespace: Ja };
var yh = Object.freeze({ __proto__: null, Node: Bl, Parser: gl, Position: ll, SourceLocation: hl, TokContext: Nl, Token: dh, TokenType: za, defaultOptions: ul, getLineInfo: cl, isIdentifierChar: Fa, isIdentifierStart: Ba, isNewLine: Xa, keywordTypes: Wa, lineBreak: Ka, lineBreakG: Ya, nonASCIIwhitespace: Ja, parse: function(e3, t2) {
  return gl.parse(e3, t2);
}, parseExpressionAt: function(e3, t2, s2) {
  return gl.parseExpressionAt(e3, t2, s2);
}, tokContexts: _l, tokTypes: Ha, tokenizer: function(e3, t2) {
  return gl.tokenizer(e3, t2);
}, version: gh });
const xh = (e3) => () => _e(function(e4) {
  return { code: "NO_FS_IN_BROWSER", message: `Cannot access the file system (via "${e4}") when using the browser build of Rollup. Make sure you supply a plugin with custom resolveId and load hooks to Rollup.`, url: Ee("plugin-development/#a-simple-example") };
}(e3)), bh = xh("fs.mkdir"), Eh = xh("fs.readFile"), vh = xh("fs.writeFile");
async function Sh(e3, t2, s2, i2, n2, r2, o2, a2, l2) {
  const h2 = await function(e4, t3, s3, i3, n3, r3, o3, a3) {
    let l3 = null, h3 = null;
    if (n3) {
      l3 = /* @__PURE__ */ new Set();
      for (const s4 of n3)
        e4 === s4.source && t3 === s4.importer && l3.add(s4.plugin);
      h3 = (e5, t4) => ({ ...e5, resolve: (e6, s4, { assertions: r4, custom: o4, isEntry: a4, skipSelf: l4 } = de) => i3(e6, s4, o4, a4, r4 || pe, l4 ? [...n3, { importer: s4, plugin: t4, source: e6 }] : n3) });
    }
    return s3.hookFirstAndGetPlugin("resolveId", [e4, t3, { assertions: a3, custom: r3, isEntry: o3 }], h3, l3);
  }(e3, t2, i2, n2, r2, o2, a2, l2);
  return null == h2 ? xh("path.resolve")() : h2[0];
}
const kh = "at position ", Ah = "at output position ";
const wh = { delete: () => false, get() {
}, has: () => false, set() {
} };
function Ih(e3) {
  return e3.startsWith(kh) || e3.startsWith(Ah) ? _e({ code: "ANONYMOUS_PLUGIN_CACHE", message: "A plugin is trying to use the Rollup cache but is not declaring a plugin name or cacheKey." }) : _e({ code: "DUPLICATE_PLUGIN_NAME", message: `The plugin name ${e3} is being used twice in the same build. Plugin names must be distinct or provide a cacheKey (please post an issue to the plugin if you are a plugin user).` });
}
async function Ph(e3, t2, s2, i2) {
  const n2 = t2.id, r2 = [];
  let o2 = null === e3.map ? null : ka(e3.map);
  const l2 = e3.code;
  let h2 = e3.ast;
  const c2 = [], u2 = [];
  let d2 = false;
  const p2 = () => d2 = true;
  let f2 = "";
  const g2 = e3.code;
  let y2;
  try {
    y2 = await s2.hookReduceArg0("transform", [g2, n2], function(e4, s3, n3) {
      let o3, a2;
      if ("string" == typeof s3)
        o3 = s3;
      else {
        if (!s3 || "object" != typeof s3)
          return e4;
        if (t2.updateOptions(s3), null == s3.code)
          return (s3.map || s3.ast) && i2(function(e5) {
            return { code: "NO_TRANSFORM_MAP_OR_AST_WITHOUT_CODE", message: `The plugin "${e5}" returned a "map" or "ast" without returning a "code". This will be ignored.` };
          }(n3.name)), e4;
        ({ code: o3, map: a2, ast: h2 } = s3);
      }
      return null !== a2 && r2.push(ka("string" == typeof a2 ? JSON.parse(a2) : a2) || { missing: true, plugin: n3.name }), o3;
    }, (e4, t3) => {
      return f2 = t3.name, { ...e4, addWatchFile(t4) {
        c2.push(t4), e4.addWatchFile(t4);
      }, cache: d2 ? e4.cache : (h3 = e4.cache, y3 = p2, { delete: (e5) => (y3(), h3.delete(e5)), get: (e5) => (y3(), h3.get(e5)), has: (e5) => (y3(), h3.has(e5)), set: (e5, t4) => (y3(), h3.set(e5, t4)) }), emitFile: (e5) => (u2.push(e5), s2.emitFile(e5)), error: (t4, s3) => ("string" == typeof t4 && (t4 = { message: t4 }), s3 && Te(t4, s3, g2, n2), t4.id = n2, t4.hook = "transform", e4.error(t4)), getCombinedSourcemap() {
        const e5 = function(e6, t4, s3, i3, n3) {
          return 0 === i3.length ? s3 : { version: 3, ...Lo(e6, t4, s3, i3, Do(n3)).traceMappings() };
        }(n2, l2, o2, r2, i2);
        if (!e5) {
          return new m(l2).generateMap({ hires: true, includeContent: true, source: n2 });
        }
        return o2 !== e5 && (o2 = e5, r2.length = 0), new a({ ...e5, file: null, sourcesContent: e5.sourcesContent });
      }, setAssetSource() {
        return this.error({ code: "INVALID_SETASSETSOURCE", message: "setAssetSource cannot be called in transform for caching reasons. Use emitFile with a source, or call setAssetSource in another hook." });
      }, warn(t4, s3) {
        "string" == typeof t4 && (t4 = { message: t4 }), s3 && Te(t4, s3, g2, n2), t4.id = n2, t4.hook = "transform", e4.warn(t4);
      } };
      var h3, y3;
    });
  } catch (e4) {
    return _e(Ze(e4, f2, { hook: "transform", id: n2 }));
  }
  return !d2 && u2.length > 0 && (t2.transformFiles = u2), { ast: h2, code: y2, customTransformCache: d2, originalCode: l2, originalSourcemap: o2, sourcemapChain: r2, transformDependencies: c2 };
}
const Ch = "resolveDependencies";
class $h {
  constructor(e3, t2, s2, i2) {
    this.graph = e3, this.modulesById = t2, this.options = s2, this.pluginDriver = i2, this.implicitEntryModules = /* @__PURE__ */ new Set(), this.indexedEntryModules = [], this.latestLoadModulesPromise = Promise.resolve(), this.moduleLoadPromises = /* @__PURE__ */ new Map(), this.modulesWithLoadedDependencies = /* @__PURE__ */ new Set(), this.nextChunkNamePriority = 0, this.nextEntryModuleIndex = 0, this.resolveId = async (e4, t3, s3, i3, n2, r2 = null) => this.getResolvedIdWithDefaults(this.getNormalizedResolvedIdWithoutDefaults(!this.options.external(e4, t3, false) && await Sh(e4, t3, this.options.preserveSymlinks, this.pluginDriver, this.resolveId, r2, s3, "boolean" == typeof i3 ? i3 : !t3, n2), t3, e4), n2), this.hasModuleSideEffects = s2.treeshake ? s2.treeshake.moduleSideEffects : () => true;
  }
  async addAdditionalModules(e3) {
    const t2 = this.extendLoadModulesPromise(Promise.all(e3.map((e4) => this.loadEntryModule(e4, false, void 0, null))));
    return await this.awaitLoadModulesPromise(), t2;
  }
  async addEntryModules(e3, t2) {
    const s2 = this.nextEntryModuleIndex;
    this.nextEntryModuleIndex += e3.length;
    const i2 = this.nextChunkNamePriority;
    this.nextChunkNamePriority += e3.length;
    const n2 = await this.extendLoadModulesPromise(Promise.all(e3.map(({ id: e4, importer: t3 }) => this.loadEntryModule(e4, true, t3, null))).then((n3) => {
      for (const [r2, o2] of n3.entries()) {
        o2.isUserDefinedEntryPoint = o2.isUserDefinedEntryPoint || t2, _h(o2, e3[r2], t2, i2 + r2);
        const n4 = this.indexedEntryModules.find((e4) => e4.module === o2);
        n4 ? n4.index = Math.min(n4.index, s2 + r2) : this.indexedEntryModules.push({ index: s2 + r2, module: o2 });
      }
      return this.indexedEntryModules.sort(({ index: e4 }, { index: t3 }) => e4 > t3 ? 1 : -1), n3;
    }));
    return await this.awaitLoadModulesPromise(), { entryModules: this.indexedEntryModules.map(({ module: e4 }) => e4), implicitEntryModules: [...this.implicitEntryModules], newEntryModules: n2 };
  }
  async emitChunk({ fileName: e3, id: t2, importer: s2, name: i2, implicitlyLoadedAfterOneOf: n2, preserveSignature: r2 }) {
    const o2 = { fileName: e3 || null, id: t2, importer: s2, name: i2 || null }, a2 = n2 ? await this.addEntryWithImplicitDependants(o2, n2) : (await this.addEntryModules([o2], false)).newEntryModules[0];
    return null != r2 && (a2.preserveSignature = r2), a2;
  }
  async preloadModule(e3) {
    return (await this.fetchModule(this.getResolvedIdWithDefaults(e3, pe), void 0, false, !e3.resolveDependencies || Ch)).info;
  }
  addEntryWithImplicitDependants(e3, t2) {
    const s2 = this.nextChunkNamePriority++;
    return this.extendLoadModulesPromise(this.loadEntryModule(e3.id, false, e3.importer, null).then(async (i2) => {
      if (_h(i2, e3, false, s2), !i2.info.isEntry) {
        this.implicitEntryModules.add(i2);
        const s3 = await Promise.all(t2.map((t3) => this.loadEntryModule(t3, false, e3.importer, i2.id)));
        for (const e4 of s3)
          i2.implicitlyLoadedAfter.add(e4);
        for (const e4 of i2.implicitlyLoadedAfter)
          e4.implicitlyLoadedBefore.add(i2);
      }
      return i2;
    }));
  }
  async addModuleSource(e3, t2, s2) {
    let i2;
    try {
      i2 = await this.graph.fileOperationQueue.run(async () => await this.pluginDriver.hookFirst("load", [e3]) ?? await Eh(e3, "utf8"));
    } catch (s3) {
      let i3 = `Could not load ${e3}`;
      throw t2 && (i3 += ` (imported by ${O(t2)})`), i3 += `: ${s3.message}`, s3.message = i3, s3;
    }
    const n2 = "string" == typeof i2 ? { code: i2 } : null != i2 && "object" == typeof i2 && "string" == typeof i2.code ? i2 : _e(function(e4) {
      return { code: "BAD_LOADER", message: `Error loading "${O(e4)}": plugin load hook should return a string, a { code, map } object, or nothing/null.` };
    }(e3)), r2 = this.graph.cachedModules.get(e3);
    if (!r2 || r2.customTransformCache || r2.originalCode !== n2.code || await this.pluginDriver.hookFirst("shouldTransformCachedModule", [{ ast: r2.ast, code: r2.code, id: r2.id, meta: r2.meta, moduleSideEffects: r2.moduleSideEffects, resolvedSources: r2.resolvedIds, syntheticNamedExports: r2.syntheticNamedExports }]))
      s2.updateOptions(n2), s2.setSource(await Ph(n2, s2, this.pluginDriver, this.options.onwarn));
    else {
      if (r2.transformFiles)
        for (const e4 of r2.transformFiles)
          this.pluginDriver.emitFile(e4);
      s2.setSource(r2);
    }
  }
  async awaitLoadModulesPromise() {
    let e3;
    do {
      e3 = this.latestLoadModulesPromise, await e3;
    } while (e3 !== this.latestLoadModulesPromise);
  }
  extendLoadModulesPromise(e3) {
    return this.latestLoadModulesPromise = Promise.all([e3, this.latestLoadModulesPromise]), this.latestLoadModulesPromise.catch(() => {
    }), e3;
  }
  async fetchDynamicDependencies(e3, t2) {
    const s2 = await Promise.all(t2.map((t3) => t3.then(async ([t4, s3]) => null === s3 ? null : "string" == typeof s3 ? (t4.resolution = s3, null) : t4.resolution = await this.fetchResolvedDependency(O(s3.id), e3.id, s3))));
    for (const t3 of s2)
      t3 && (e3.dynamicDependencies.add(t3), t3.dynamicImporters.push(e3.id));
  }
  async fetchModule({ assertions: e3, id: t2, meta: s2, moduleSideEffects: i2, syntheticNamedExports: n2 }, r2, o2, a2) {
    const l2 = this.modulesById.get(t2);
    if (l2 instanceof xr)
      return r2 && sr(e3, l2.info.assertions) && this.options.onwarn(He(l2.info.assertions, e3, t2, r2)), await this.handleExistingModule(l2, o2, a2), l2;
    const h2 = new xr(this.graph, t2, this.options, o2, i2, n2, s2, e3);
    this.modulesById.set(t2, h2), this.graph.watchFiles[t2] = true;
    const c2 = this.addModuleSource(t2, r2, h2).then(() => [this.getResolveStaticDependencyPromises(h2), this.getResolveDynamicImportPromises(h2), u2]), u2 = Rh(c2).then(() => this.pluginDriver.hookParallel("moduleParsed", [h2.info]));
    u2.catch(() => {
    }), this.moduleLoadPromises.set(h2, c2);
    const d2 = await c2;
    return a2 ? a2 === Ch && await u2 : await this.fetchModuleDependencies(h2, ...d2), h2;
  }
  async fetchModuleDependencies(e3, t2, s2, i2) {
    this.modulesWithLoadedDependencies.has(e3) || (this.modulesWithLoadedDependencies.add(e3), await Promise.all([this.fetchStaticDependencies(e3, t2), this.fetchDynamicDependencies(e3, s2)]), e3.linkImports(), await i2);
  }
  fetchResolvedDependency(e3, t2, s2) {
    if (s2.external) {
      const { assertions: i2, external: n2, id: r2, moduleSideEffects: o2, meta: a2 } = s2;
      let l2 = this.modulesById.get(r2);
      if (l2) {
        if (!(l2 instanceof ct))
          return _e(function(e4, t3) {
            return { code: "INVALID_EXTERNAL_ID", message: `"${e4}" is imported as an external by "${O(t3)}", but is already an existing non-external module id.` };
          }(e3, t2));
        sr(l2.info.assertions, i2) && this.options.onwarn(He(l2.info.assertions, i2, e3, t2));
      } else
        l2 = new ct(this.options, r2, o2, a2, "absolute" !== n2 && S(r2), i2), this.modulesById.set(r2, l2);
      return Promise.resolve(l2);
    }
    return this.fetchModule(s2, t2, false, false);
  }
  async fetchStaticDependencies(e3, t2) {
    for (const s2 of await Promise.all(t2.map((t3) => t3.then(([t4, s3]) => this.fetchResolvedDependency(t4, e3.id, s3)))))
      e3.dependencies.add(s2), s2.importers.push(e3.id);
    if (!this.options.treeshake || "no-treeshake" === e3.info.moduleSideEffects)
      for (const t3 of e3.dependencies)
        t3 instanceof xr && (t3.importedFromNotTreeshaken = true);
  }
  getNormalizedResolvedIdWithoutDefaults(e3, t2, s2) {
    const { makeAbsoluteExternalsRelative: i2 } = this.options;
    if (e3) {
      if ("object" == typeof e3) {
        const n4 = e3.external || this.options.external(e3.id, t2, true);
        return { ...e3, external: n4 && ("relative" === n4 || !S(e3.id) || true === n4 && Th(e3.id, s2, i2) || "absolute") };
      }
      const n3 = this.options.external(e3, t2, true);
      return { external: n3 && (Th(e3, s2, i2) || "absolute"), id: n3 && i2 ? Nh(e3, t2) : e3 };
    }
    const n2 = i2 ? Nh(s2, t2) : s2;
    return false === e3 || this.options.external(n2, t2, true) ? { external: Th(n2, s2, i2) || "absolute", id: n2 } : null;
  }
  getResolveDynamicImportPromises(e3) {
    return e3.dynamicImports.map(async (t2) => {
      const s2 = await this.resolveDynamicImport(e3, "string" == typeof t2.argument ? t2.argument : t2.argument.esTreeNode, e3.id, function(e4) {
        var _a2, _b, _c2;
        const t3 = (_c2 = (_b = (_a2 = e4.arguments) == null ? void 0 : _a2[0]) == null ? void 0 : _b.properties.find((e5) => "assert" === tr(e5))) == null ? void 0 : _c2.value;
        if (!t3)
          return pe;
        const s3 = t3.properties.map((e5) => {
          const t4 = tr(e5);
          return "string" == typeof t4 && "string" == typeof e5.value.value ? [t4, e5.value.value] : null;
        }).filter((e5) => !!e5);
        return s3.length > 0 ? Object.fromEntries(s3) : pe;
      }(t2.node));
      return s2 && "object" == typeof s2 && (t2.id = s2.id), [t2, s2];
    });
  }
  getResolveStaticDependencyPromises(e3) {
    return Array.from(e3.sourcesWithAssertions, async ([t2, s2]) => [t2, e3.resolvedIds[t2] = e3.resolvedIds[t2] || this.handleInvalidResolvedId(await this.resolveId(t2, e3.id, pe, false, s2), t2, e3.id, s2)]);
  }
  getResolvedIdWithDefaults(e3, t2) {
    if (!e3)
      return null;
    const s2 = e3.external || false;
    return { assertions: e3.assertions || t2, external: s2, id: e3.id, meta: e3.meta || {}, moduleSideEffects: e3.moduleSideEffects ?? this.hasModuleSideEffects(e3.id, !!s2), resolvedBy: e3.resolvedBy ?? "rollup", syntheticNamedExports: e3.syntheticNamedExports ?? false };
  }
  async handleExistingModule(e3, t2, s2) {
    const i2 = this.moduleLoadPromises.get(e3);
    if (s2)
      return s2 === Ch ? Rh(i2) : i2;
    if (t2) {
      e3.info.isEntry = true, this.implicitEntryModules.delete(e3);
      for (const t3 of e3.implicitlyLoadedAfter)
        t3.implicitlyLoadedBefore.delete(e3);
      e3.implicitlyLoadedAfter.clear();
    }
    return this.fetchModuleDependencies(e3, ...await i2);
  }
  handleInvalidResolvedId(e3, t2, s2, i2) {
    return null === e3 ? k(t2) ? _e(function(e4, t3) {
      return { code: je, exporter: e4, id: t3, message: `Could not resolve "${e4}" from "${O(t3)}"` };
    }(t2, s2)) : (this.options.onwarn(function(e4, t3) {
      return { code: je, exporter: e4, id: t3, message: `"${e4}" is imported by "${O(t3)}", but could not be resolved – treating it as an external dependency.`, url: Ee("troubleshooting/#warning-treating-module-as-external-dependency") };
    }(t2, s2)), { assertions: i2, external: true, id: t2, meta: {}, moduleSideEffects: this.hasModuleSideEffects(t2, true), resolvedBy: "rollup", syntheticNamedExports: false }) : (e3.external && e3.syntheticNamedExports && this.options.onwarn(function(e4, t3) {
      return { code: "EXTERNAL_SYNTHETIC_EXPORTS", exporter: e4, message: `External "${e4}" cannot have "syntheticNamedExports" enabled (imported by "${O(t3)}").` };
    }(t2, s2)), e3);
  }
  async loadEntryModule(e3, t2, s2, i2) {
    const n2 = await Sh(e3, s2, this.options.preserveSymlinks, this.pluginDriver, this.resolveId, null, pe, true, pe);
    return null == n2 ? _e(null === i2 ? function(e4) {
      return { code: ze, message: `Could not resolve entry module "${O(e4)}".` };
    }(e3) : function(e4, t3) {
      return { code: De, message: `Module "${O(e4)}" that should be implicitly loaded before "${O(t3)}" could not be resolved.` };
    }(e3, i2)) : false === n2 || "object" == typeof n2 && n2.external ? _e(null === i2 ? function(e4) {
      return { code: ze, message: `Entry module "${O(e4)}" cannot be external.` };
    }(e3) : function(e4, t3) {
      return { code: De, message: `Module "${O(e4)}" that should be implicitly loaded before "${O(t3)}" cannot be external.` };
    }(e3, i2)) : this.fetchModule(this.getResolvedIdWithDefaults("object" == typeof n2 ? n2 : { id: n2 }, pe), void 0, t2, false);
  }
  async resolveDynamicImport(e3, t2, s2, i2) {
    const n2 = await this.pluginDriver.hookFirst("resolveDynamicImport", [t2, s2, { assertions: i2 }]);
    if ("string" != typeof t2)
      return "string" == typeof n2 ? n2 : n2 ? this.getResolvedIdWithDefaults(n2, i2) : null;
    if (null == n2) {
      const n3 = e3.resolvedIds[t2];
      return n3 ? (sr(n3.assertions, i2) && this.options.onwarn(He(n3.assertions, i2, t2, s2)), n3) : e3.resolvedIds[t2] = this.handleInvalidResolvedId(await this.resolveId(t2, e3.id, pe, false, i2), t2, e3.id, i2);
    }
    return this.handleInvalidResolvedId(this.getResolvedIdWithDefaults(this.getNormalizedResolvedIdWithoutDefaults(n2, s2, t2), i2), t2, s2, i2);
  }
}
function Nh(e3, t2) {
  return k(e3) ? t2 ? $(t2, "..", e3) : $(e3) : e3;
}
function _h(e3, { fileName: t2, name: s2 }, i2, n2) {
  var _a2;
  if (null !== t2)
    e3.chunkFileNames.add(t2);
  else if (null !== s2) {
    let t3 = 0;
    for (; ((_a2 = e3.chunkNames[t3]) == null ? void 0 : _a2.priority) < n2; )
      t3++;
    e3.chunkNames.splice(t3, 0, { isUserDefined: i2, name: s2, priority: n2 });
  }
}
function Th(e3, t2, s2) {
  return true === s2 || "ifRelativeSource" === s2 && k(t2) || !S(e3);
}
async function Rh(e3) {
  const [t2, s2] = await e3;
  return Promise.all([...t2, ...s2]);
}
class Mh extends $s {
  constructor() {
    super(), this.parent = null, this.variables.set("undefined", new jn());
  }
  findVariable(e3) {
    let t2 = this.variables.get(e3);
    return t2 || (t2 = new Ys(e3), this.variables.set(e3, t2)), t2;
  }
}
function Oh(e3, { bundle: t2 }, s2) {
  t2[ao].has(e3.toLowerCase()) ? s2(function(e4) {
    return { code: "FILE_NAME_CONFLICT", message: `The emitted file "${e4}" overwrites a previously emitted file of the same name.` };
  }(e3)) : t2[e3] = lo;
}
function Dh(e3, t2, s2) {
  if (!("string" == typeof e3 || e3 instanceof Uint8Array)) {
    const e4 = t2.fileName || t2.name || s2;
    return _e(it(`Could not set source for ${"string" == typeof e4 ? `asset "${e4}"` : "unnamed asset"}, asset source needs to be a string, Uint8Array or Buffer.`));
  }
  return e3;
}
class Lh {
  constructor(e3, t2, s2) {
    this.graph = e3, this.options = t2, this.facadeChunkByModule = null, this.nextIdBase = 1, this.output = null, this.outputFileEmitters = [], this.emitFile = (e4) => function(e5) {
      return Boolean(e5 && ("asset" === e5.type || "chunk" === e5.type));
    }(e4) ? function(e5) {
      const t3 = e5.fileName || e5.name;
      return !t3 || "string" == typeof t3 && !D(t3);
    }(e4) ? "chunk" === e4.type ? this.emitChunk(e4) : this.emitAsset(e4) : _e(it(`The "fileName" or "name" properties of emitted files must be strings that are neither absolute nor relative paths, received "${e4.fileName || e4.name}".`)) : _e(it(`Emitted files must be of type "asset" or "chunk", received "${e4 && e4.type}".`)), this.finaliseAssets = () => {
      for (const [e4, t3] of this.filesByReferenceId)
        if ("asset" === t3.type && "string" != typeof t3.fileName)
          return _e({ code: "ASSET_SOURCE_MISSING", message: `Plugin error creating asset "${t3.name || e4}" - no asset source set.` });
    }, this.getFileName = (e4) => {
      const t3 = this.filesByReferenceId.get(e4);
      return t3 ? "chunk" === t3.type ? (s3 = t3, i2 = this.facadeChunkByModule, s3.fileName ? s3.fileName : i2 ? i2.get(s3.module).getFileName() : _e({ code: "CHUNK_NOT_GENERATED", message: `Plugin error - Unable to get file name for emitted chunk "${s3.fileName || s3.name}". You can only get file names once chunks have been generated after the "renderStart" hook.` })) : function(e5, t4) {
        return "string" != typeof e5.fileName ? _e({ code: "ASSET_NOT_FINALISED", message: `Plugin error - Unable to get file name for asset "${e5.name || t4}". Ensure that the source is set and that generate is called first. If you reference assets via import.meta.ROLLUP_FILE_URL_<referenceId>, you need to either have set their source after "renderStart" or need to provide an explicit "fileName" when emitting them.` }) : e5.fileName;
      }(t3, e4) : _e({ code: "FILE_NOT_FOUND", message: `Plugin error - Unable to get file name for unknown file "${e4}".` });
      var s3, i2;
    }, this.setAssetSource = (e4, t3) => {
      const s3 = this.filesByReferenceId.get(e4);
      if (!s3)
        return _e({ code: "ASSET_NOT_FOUND", message: `Plugin error - Unable to set the source for unknown asset "${e4}".` });
      if ("asset" !== s3.type)
        return _e(it(`Asset sources can only be set for emitted assets but "${e4}" is an emitted chunk.`));
      if (void 0 !== s3.source)
        return _e({ code: "ASSET_SOURCE_ALREADY_SET", message: `Unable to set the source for asset "${s3.name || e4}", source already set.` });
      const i2 = Dh(t3, s3, e4);
      if (this.output)
        this.finalizeAsset(s3, i2, e4, this.output);
      else {
        s3.source = i2;
        for (const t4 of this.outputFileEmitters)
          t4.finalizeAsset(s3, i2, e4, t4.output);
      }
    }, this.setChunkInformation = (e4) => {
      this.facadeChunkByModule = e4;
    }, this.setOutputBundle = (e4, t3) => {
      const s3 = this.output = { bundle: e4, fileNamesBySource: /* @__PURE__ */ new Map(), outputOptions: t3 };
      for (const e5 of this.filesByReferenceId.values())
        e5.fileName && Oh(e5.fileName, s3, this.options.onwarn);
      for (const [e5, t4] of this.filesByReferenceId)
        "asset" === t4.type && void 0 !== t4.source && this.finalizeAsset(t4, t4.source, e5, s3);
    }, this.filesByReferenceId = s2 ? new Map(s2.filesByReferenceId) : /* @__PURE__ */ new Map(), s2 == null ? void 0 : s2.addOutputFileEmitter(this);
  }
  addOutputFileEmitter(e3) {
    this.outputFileEmitters.push(e3);
  }
  assignReferenceId(e3, t2) {
    let s2 = t2;
    do {
      s2 = Sa().update(s2).digest("hex").slice(0, 8);
    } while (this.filesByReferenceId.has(s2) || this.outputFileEmitters.some(({ filesByReferenceId: e4 }) => e4.has(s2)));
    this.filesByReferenceId.set(s2, e3);
    for (const { filesByReferenceId: t3 } of this.outputFileEmitters)
      t3.set(s2, e3);
    return s2;
  }
  emitAsset(e3) {
    const t2 = void 0 === e3.source ? void 0 : Dh(e3.source, e3, null), s2 = { fileName: e3.fileName, name: e3.name, needsCodeReference: !!e3.needsCodeReference, source: t2, type: "asset" }, i2 = this.assignReferenceId(s2, e3.fileName || e3.name || String(this.nextIdBase++));
    if (this.output)
      this.emitAssetWithReferenceId(s2, i2, this.output);
    else
      for (const e4 of this.outputFileEmitters)
        e4.emitAssetWithReferenceId(s2, i2, e4.output);
    return i2;
  }
  emitAssetWithReferenceId(e3, t2, s2) {
    const { fileName: i2, source: n2 } = e3;
    i2 && Oh(i2, s2, this.options.onwarn), void 0 !== n2 && this.finalizeAsset(e3, n2, t2, s2);
  }
  emitChunk(e3) {
    if (this.graph.phase > Zn.LOAD_AND_PARSE)
      return _e({ code: Oe, message: "Cannot emit chunks after module loading has finished." });
    if ("string" != typeof e3.id)
      return _e(it(`Emitted chunks need to have a valid string id, received "${e3.id}"`));
    const t2 = { fileName: e3.fileName, module: null, name: e3.name || e3.id, type: "chunk" };
    return this.graph.moduleLoader.emitChunk(e3).then((e4) => t2.module = e4).catch(() => {
    }), this.assignReferenceId(t2, e3.id);
  }
  finalizeAsset(e3, t2, s2, { bundle: i2, fileNamesBySource: n2, outputOptions: r2 }) {
    let o2 = e3.fileName;
    if (!o2) {
      const s3 = function(e4) {
        return Sa().update(e4).digest("hex");
      }(t2);
      o2 = n2.get(s3), o2 || (o2 = function(e4, t3, s4, i3, n3) {
        const r3 = i3.sanitizeFileName(e4 || "asset");
        return co(ho("function" == typeof i3.assetFileNames ? i3.assetFileNames({ name: e4, source: t3, type: "asset" }) : i3.assetFileNames, "output.assetFileNames", { ext: () => P(r3).slice(1), extname: () => P(r3), hash: (e5) => s4.slice(0, Math.max(0, e5 || 8)), name: () => r3.slice(0, Math.max(0, r3.length - P(r3).length)) }), n3);
      }(e3.name, t2, s3, r2, i2), n2.set(s3, o2));
    }
    const a2 = { ...e3, fileName: o2, source: t2 };
    this.filesByReferenceId.set(s2, a2), i2[o2] = { fileName: o2, name: e3.name, needsCodeReference: e3.needsCodeReference, source: t2, type: "asset" };
  }
}
function Vh(t2, s2, i2, n2, r2, o2) {
  let a2, l2 = true;
  if ("string" != typeof t2.cacheKey && (t2.name.startsWith(kh) || t2.name.startsWith(Ah) || o2.has(t2.name) ? l2 = false : o2.add(t2.name)), s2)
    if (l2) {
      const e3 = t2.cacheKey || t2.name;
      c2 = s2[e3] || (s2[e3] = /* @__PURE__ */ Object.create(null)), a2 = { delete: (e4) => delete c2[e4], get(e4) {
        const t3 = c2[e4];
        if (t3)
          return t3[0] = 0, t3[1];
      }, has(e4) {
        const t3 = c2[e4];
        return !!t3 && (t3[0] = 0, true);
      }, set(e4, t3) {
        c2[e4] = [0, t3];
      } };
    } else
      h2 = t2.name, a2 = { delete: () => Ih(h2), get: () => Ih(h2), has: () => Ih(h2), set: () => Ih(h2) };
  else
    a2 = wh;
  var h2, c2;
  return { addWatchFile(e3) {
    if (i2.phase >= Zn.GENERATE)
      return this.error({ code: Oe, message: 'Cannot call "addWatchFile" after the build has finished.' });
    i2.watchFiles[e3] = true;
  }, cache: a2, emitFile: r2.emitFile.bind(r2), error: (e3) => _e(Ze(e3, t2.name)), getFileName: r2.getFileName, getModuleIds: () => i2.modulesById.keys(), getModuleInfo: i2.getModuleInfo, getWatchFiles: () => Object.keys(i2.watchFiles), load: (e3) => i2.moduleLoader.preloadModule(e3), meta: { rollupVersion: e, watchMode: i2.watchMode }, get moduleIds() {
    const e3 = i2.modulesById.keys();
    return function* () {
      nt(`Accessing "this.moduleIds" on the plugin context by plugin ${t2.name} is deprecated. The "this.getModuleIds" plugin context function should be used instead.`, "plugin-development/#this-getmoduleids", true, n2, t2.name), yield* e3;
    }();
  }, parse: i2.contextParse.bind(i2), resolve: (e3, s3, { assertions: n3, custom: r3, isEntry: o3, skipSelf: a3 } = de) => i2.moduleLoader.resolveId(e3, s3, r3, o3, n3 || pe, a3 ? [{ importer: s3, plugin: t2, source: e3 }] : null), setAssetSource: r2.setAssetSource, warn(e3) {
    "string" == typeof e3 && (e3 = { message: e3 }), e3.code && (e3.pluginCode = e3.code), e3.code = "PLUGIN_WARNING", e3.plugin = t2.name, n2.onwarn(e3);
  } };
}
const Bh = Object.keys({ buildEnd: 1, buildStart: 1, closeBundle: 1, closeWatcher: 1, load: 1, moduleParsed: 1, options: 1, resolveDynamicImport: 1, resolveId: 1, shouldTransformCachedModule: 1, transform: 1, watchChange: 1 });
class Fh {
  constructor(e3, t2, s2, i2, n2) {
    this.graph = e3, this.options = t2, this.pluginCache = i2, this.sortedPlugins = /* @__PURE__ */ new Map(), this.unfulfilledActions = /* @__PURE__ */ new Set(), this.fileEmitter = new Lh(e3, t2, n2 && n2.fileEmitter), this.emitFile = this.fileEmitter.emitFile.bind(this.fileEmitter), this.getFileName = this.fileEmitter.getFileName.bind(this.fileEmitter), this.finaliseAssets = this.fileEmitter.finaliseAssets.bind(this.fileEmitter), this.setChunkInformation = this.fileEmitter.setChunkInformation.bind(this.fileEmitter), this.setOutputBundle = this.fileEmitter.setOutputBundle.bind(this.fileEmitter), this.plugins = [...n2 ? n2.plugins : [], ...s2];
    const r2 = /* @__PURE__ */ new Set();
    if (this.pluginContexts = new Map(this.plugins.map((s3) => [s3, Vh(s3, i2, e3, t2, this.fileEmitter, r2)])), n2)
      for (const e4 of s2)
        for (const s3 of Bh)
          s3 in e4 && t2.onwarn((o2 = e4.name, { code: "INPUT_HOOK_IN_OUTPUT_PLUGIN", message: `The "${s3}" hook used by the output plugin ${o2} is a build time hook and will not be run for that plugin. Either this plugin cannot be used as an output plugin, or it should have an option to configure it as an output plugin.` }));
    var o2;
  }
  createOutputPluginDriver(e3) {
    return new Fh(this.graph, this.options, e3, this.pluginCache, this);
  }
  getUnfulfilledHookActions() {
    return this.unfulfilledActions;
  }
  hookFirst(e3, t2, s2, i2) {
    return this.hookFirstAndGetPlugin(e3, t2, s2, i2).then((e4) => e4 && e4[0]);
  }
  async hookFirstAndGetPlugin(e3, t2, s2, i2) {
    for (const n2 of this.getSortedPlugins(e3)) {
      if (i2 == null ? void 0 : i2.has(n2))
        continue;
      const r2 = await this.runHook(e3, t2, n2, s2);
      if (null != r2)
        return [r2, n2];
    }
    return null;
  }
  hookFirstSync(e3, t2, s2) {
    for (const i2 of this.getSortedPlugins(e3)) {
      const n2 = this.runHookSync(e3, t2, i2, s2);
      if (null != n2)
        return n2;
    }
    return null;
  }
  async hookParallel(e3, t2, s2) {
    const i2 = [];
    for (const n2 of this.getSortedPlugins(e3))
      n2[e3].sequential ? (await Promise.all(i2), i2.length = 0, await this.runHook(e3, t2, n2, s2)) : i2.push(this.runHook(e3, t2, n2, s2));
    await Promise.all(i2);
  }
  hookReduceArg0(e3, [t2, ...s2], i2, n2) {
    let r2 = Promise.resolve(t2);
    for (const t3 of this.getSortedPlugins(e3))
      r2 = r2.then((r3) => this.runHook(e3, [r3, ...s2], t3, n2).then((e4) => i2.call(this.pluginContexts.get(t3), r3, e4, t3)));
    return r2;
  }
  hookReduceArg0Sync(e3, [t2, ...s2], i2, n2) {
    for (const r2 of this.getSortedPlugins(e3)) {
      const o2 = [t2, ...s2], a2 = this.runHookSync(e3, o2, r2, n2);
      t2 = i2.call(this.pluginContexts.get(r2), t2, a2, r2);
    }
    return t2;
  }
  async hookReduceValue(e3, t2, s2, i2) {
    const n2 = [], r2 = [];
    for (const t3 of this.getSortedPlugins(e3, Uh))
      t3[e3].sequential ? (n2.push(...await Promise.all(r2)), r2.length = 0, n2.push(await this.runHook(e3, s2, t3))) : r2.push(this.runHook(e3, s2, t3));
    return n2.push(...await Promise.all(r2)), n2.reduce(i2, await t2);
  }
  hookReduceValueSync(e3, t2, s2, i2, n2) {
    let r2 = t2;
    for (const t3 of this.getSortedPlugins(e3)) {
      const o2 = this.runHookSync(e3, s2, t3, n2);
      r2 = i2.call(this.pluginContexts.get(t3), r2, o2, t3);
    }
    return r2;
  }
  hookSeq(e3, t2, s2) {
    let i2 = Promise.resolve();
    for (const n2 of this.getSortedPlugins(e3))
      i2 = i2.then(() => this.runHook(e3, t2, n2, s2));
    return i2.then(Gh);
  }
  getSortedPlugins(e3, t2) {
    return F(this.sortedPlugins, e3, () => zh(e3, this.plugins, t2));
  }
  runHook(e3, t2, s2, i2) {
    const n2 = s2[e3], r2 = "object" == typeof n2 ? n2.handler : n2;
    let o2 = this.pluginContexts.get(s2);
    i2 && (o2 = i2(o2, s2));
    let a2 = null;
    return Promise.resolve().then(() => {
      if ("function" != typeof r2)
        return r2;
      const i3 = r2.apply(o2, t2);
      return (i3 == null ? void 0 : i3.then) ? (a2 = [s2.name, e3, t2], this.unfulfilledActions.add(a2), Promise.resolve(i3).then((e4) => (this.unfulfilledActions.delete(a2), e4))) : i3;
    }).catch((t3) => (null !== a2 && this.unfulfilledActions.delete(a2), _e(Ze(t3, s2.name, { hook: e3 }))));
  }
  runHookSync(e3, t2, s2, i2) {
    const n2 = s2[e3], r2 = "object" == typeof n2 ? n2.handler : n2;
    let o2 = this.pluginContexts.get(s2);
    i2 && (o2 = i2(o2, s2));
    try {
      return r2.apply(o2, t2);
    } catch (t3) {
      return _e(Ze(t3, s2.name, { hook: e3 }));
    }
  }
}
function zh(e3, t2, s2 = jh) {
  const i2 = [], n2 = [], r2 = [];
  for (const o2 of t2) {
    const t3 = o2[e3];
    if (t3) {
      if ("object" == typeof t3) {
        if (s2(t3.handler, e3, o2), "pre" === t3.order) {
          i2.push(o2);
          continue;
        }
        if ("post" === t3.order) {
          r2.push(o2);
          continue;
        }
      } else
        s2(t3, e3, o2);
      n2.push(o2);
    }
  }
  return [...i2, ...n2, ...r2];
}
function jh(e3, t2, s2) {
  "function" != typeof e3 && _e(function(e4, t3) {
    return { code: Me, hook: e4, message: `Error running plugin hook "${e4}" for plugin "${t3}", expected a function hook or an object with a "handler" function.`, plugin: t3 };
  }(t2, s2.name));
}
function Uh(e3, t2, s2) {
  if ("string" != typeof e3 && "function" != typeof e3)
    return _e(function(e4, t3) {
      return { code: Me, hook: e4, message: `Error running plugin hook "${e4}" for plugin "${t3}", expected a string, a function hook or an object with a "handler" string or function.`, plugin: t3 };
    }(t2, s2.name));
}
function Gh() {
}
class Wh {
  constructor(e3) {
    this.maxParallel = e3, this.queue = [], this.workerCount = 0;
  }
  run(e3) {
    return new Promise((t2, s2) => {
      this.queue.push({ reject: s2, resolve: t2, task: e3 }), this.work();
    });
  }
  async work() {
    if (this.workerCount >= this.maxParallel)
      return;
    let e3;
    for (this.workerCount++; e3 = this.queue.shift(); ) {
      const { reject: t2, resolve: s2, task: i2 } = e3;
      try {
        s2(await i2());
      } catch (e4) {
        t2(e4);
      }
    }
    this.workerCount--;
  }
}
class qh {
  constructor(e3, t2) {
    var _a2, _b;
    if (this.options = e3, this.astLru = function(e4) {
      var t3, s2, i2, n2 = e4 || 1;
      function r2(e5, r3) {
        ++t3 > n2 && (i2 = s2, o2(1), ++t3), s2[e5] = r3;
      }
      function o2(e5) {
        t3 = 0, s2 = /* @__PURE__ */ Object.create(null), e5 || (i2 = /* @__PURE__ */ Object.create(null));
      }
      return o2(), { clear: o2, has: function(e5) {
        return void 0 !== s2[e5] || void 0 !== i2[e5];
      }, get: function(e5) {
        var t4 = s2[e5];
        return void 0 !== t4 ? t4 : void 0 !== (t4 = i2[e5]) ? (r2(e5, t4), t4) : void 0;
      }, set: function(e5, t4) {
        void 0 !== s2[e5] ? s2[e5] = t4 : r2(e5, t4);
      } };
    }(5), this.cachedModules = /* @__PURE__ */ new Map(), this.deoptimizationTracker = new J(), this.entryModules = [], this.modulesById = /* @__PURE__ */ new Map(), this.needsTreeshakingPass = false, this.phase = Zn.LOAD_AND_PARSE, this.scope = new Mh(), this.watchFiles = /* @__PURE__ */ Object.create(null), this.watchMode = false, this.externalModules = [], this.implicitEntryModules = [], this.modules = [], this.getModuleInfo = (e4) => {
      const t3 = this.modulesById.get(e4);
      return t3 ? t3.info : null;
    }, false !== e3.cache) {
      if ((_a2 = e3.cache) == null ? void 0 : _a2.modules)
        for (const t3 of e3.cache.modules)
          this.cachedModules.set(t3.id, t3);
      this.pluginCache = ((_b = e3.cache) == null ? void 0 : _b.plugins) || /* @__PURE__ */ Object.create(null);
      for (const e4 in this.pluginCache) {
        const t3 = this.pluginCache[e4];
        for (const e5 of Object.values(t3))
          e5[0]++;
      }
    }
    if (t2) {
      this.watchMode = true;
      const e4 = (...e5) => this.pluginDriver.hookParallel("watchChange", e5), s2 = () => this.pluginDriver.hookParallel("closeWatcher", []);
      t2.onCurrentRun("change", e4), t2.onCurrentRun("close", s2);
    }
    this.pluginDriver = new Fh(this, e3, e3.plugins, this.pluginCache), this.acornParser = gl.extend(...e3.acornInjectPlugins), this.moduleLoader = new $h(this, this.modulesById, this.options, this.pluginDriver), this.fileOperationQueue = new Wh(e3.maxParallelFileOps), this.pureFunctions = (({ treeshake: e4 }) => {
      const t3 = /* @__PURE__ */ Object.create(null);
      for (const s2 of e4 ? e4.manualPureFunctions : []) {
        let e5 = t3;
        for (const t4 of s2.split("."))
          e5 = e5[t4] || (e5[t4] = /* @__PURE__ */ Object.create(null));
        e5[Ms] = true;
      }
      return t3;
    })(e3);
  }
  async build() {
    ur("generate module graph", 2), await this.generateModuleGraph(), dr("generate module graph", 2), ur("sort and bind modules", 2), this.phase = Zn.ANALYSE, this.sortModules(), dr("sort and bind modules", 2), ur("mark included statements", 2), this.includeStatements(), dr("mark included statements", 2), this.phase = Zn.GENERATE;
  }
  contextParse(e3, t2 = {}) {
    const s2 = t2.onComment, i2 = [];
    t2.onComment = s2 && "function" == typeof s2 ? (e4, n3, r2, o2, ...a2) => (i2.push({ end: o2, start: r2, type: e4 ? "Block" : "Line", value: n3 }), s2.call(t2, e4, n3, r2, o2, ...a2)) : i2;
    const n2 = this.acornParser.parse(e3, { ...this.options.acorn, ...t2 });
    return "object" == typeof s2 && s2.push(...i2), t2.onComment = s2, function(e4, t3, s3) {
      const i3 = [], n3 = [];
      for (const t4 of e4)
        Xt.test(t4.value) ? i3.push(t4) : jt.test(t4.value) && n3.push(t4);
      for (const e5 of n3)
        Qt(t3, e5, false);
      Wt(t3, { annotationIndex: 0, annotations: i3, code: s3 });
    }(i2, n2, e3), n2;
  }
  getCache() {
    for (const e3 in this.pluginCache) {
      const t2 = this.pluginCache[e3];
      let s2 = true;
      for (const [e4, i2] of Object.entries(t2))
        i2[0] >= this.options.experimentalCacheExpiry ? delete t2[e4] : s2 = false;
      s2 && delete this.pluginCache[e3];
    }
    return { modules: this.modules.map((e3) => e3.toJSON()), plugins: this.pluginCache };
  }
  async generateModuleGraph() {
    var e3;
    if ({ entryModules: this.entryModules, implicitEntryModules: this.implicitEntryModules } = await this.moduleLoader.addEntryModules((e3 = this.options.input, Array.isArray(e3) ? e3.map((e4) => ({ fileName: null, id: e4, implicitlyLoadedAfter: [], importer: void 0, name: null })) : Object.entries(e3).map(([e4, t2]) => ({ fileName: null, id: t2, implicitlyLoadedAfter: [], importer: void 0, name: e4 }))), true), 0 === this.entryModules.length)
      throw new Error("You must supply options.input to rollup");
    for (const e4 of this.modulesById.values())
      e4 instanceof xr ? this.modules.push(e4) : this.externalModules.push(e4);
  }
  includeStatements() {
    const e3 = [...this.entryModules, ...this.implicitEntryModules];
    for (const t2 of e3)
      mr(t2);
    if (this.options.treeshake) {
      let t2 = 1;
      do {
        ur(`treeshaking pass ${t2}`, 3), this.needsTreeshakingPass = false;
        for (const e4 of this.modules)
          e4.isExecuted && ("no-treeshake" === e4.info.moduleSideEffects ? e4.includeAllInBundle() : e4.include());
        if (1 === t2)
          for (const t3 of e3)
            false !== t3.preserveSignature && (t3.includeAllExports(false), this.needsTreeshakingPass = true);
        dr("treeshaking pass " + t2++, 3);
      } while (this.needsTreeshakingPass);
    } else
      for (const e4 of this.modules)
        e4.includeAllInBundle();
    for (const e4 of this.externalModules)
      e4.warnUnusedImports();
    for (const e4 of this.implicitEntryModules)
      for (const t2 of e4.implicitlyLoadedAfter)
        t2.info.isEntry || t2.isIncluded() || _e(Je(t2));
  }
  sortModules() {
    const { orderedModules: e3, cyclePaths: t2 } = function(e4) {
      let t3 = 0;
      const s2 = [], i2 = /* @__PURE__ */ new Set(), n2 = /* @__PURE__ */ new Set(), r2 = /* @__PURE__ */ new Map(), o2 = [], a2 = (e5) => {
        if (e5 instanceof xr) {
          for (const t4 of e5.dependencies)
            r2.has(t4) ? i2.has(t4) || s2.push(_o(t4, e5, r2)) : (r2.set(t4, e5), a2(t4));
          for (const t4 of e5.implicitlyLoadedBefore)
            n2.add(t4);
          for (const { resolution: t4 } of e5.dynamicImports)
            t4 instanceof xr && n2.add(t4);
          o2.push(e5);
        }
        e5.execIndex = t3++, i2.add(e5);
      };
      for (const t4 of e4)
        r2.has(t4) || (r2.set(t4, null), a2(t4));
      for (const e5 of n2)
        r2.has(e5) || (r2.set(e5, null), a2(e5));
      return { cyclePaths: s2, orderedModules: o2 };
    }(this.entryModules);
    for (const e4 of t2)
      this.options.onwarn(We(e4));
    this.modules = e3;
    for (const e4 of this.modules)
      e4.bindReferences();
    this.warnForMissingExports();
  }
  warnForMissingExports() {
    for (const e3 of this.modules)
      for (const t2 of e3.importDescriptions.values())
        "*" === t2.name || t2.module.getVariableForExportName(t2.name)[0] || e3.warn(Qe(t2.name, e3.id, t2.module.id), t2.start);
  }
}
function Hh(e3, t2) {
  return t2();
}
const Kh = "{".charCodeAt(0), Yh = " ".charCodeAt(0), Xh = "assert";
function Qh(e3) {
  const t2 = e3.acorn || yh, { tokTypes: s2, TokenType: i2 } = t2;
  return class extends e3 {
    constructor(...e4) {
      super(...e4), this.assertToken = new i2(Xh);
    }
    _codeAt(e4) {
      return this.input.charCodeAt(e4);
    }
    _eat(e4) {
      this.type !== e4 && this.unexpected(), this.next();
    }
    readToken(e4) {
      let t3 = 0;
      for (; t3 < Xh.length; t3++)
        if (this._codeAt(this.pos + t3) !== Xh.charCodeAt(t3))
          return super.readToken(e4);
      for (; this._codeAt(this.pos + t3) !== Kh; t3++)
        if (this._codeAt(this.pos + t3) !== Yh)
          return super.readToken(e4);
      return "{" === this.type.label ? super.readToken(e4) : (this.pos += Xh.length, this.finishToken(this.assertToken));
    }
    parseDynamicImport(e4) {
      if (this.next(), e4.source = this.parseMaybeAssign(), this.eat(s2.comma)) {
        const t3 = this.parseObj(false);
        e4.arguments = [t3];
      }
      return this._eat(s2.parenR), this.finishNode(e4, "ImportExpression");
    }
    parseExport(e4, t3) {
      if (this.next(), this.eat(s2.star)) {
        if (this.options.ecmaVersion >= 11 && (this.eatContextual("as") ? (e4.exported = this.parseIdent(true), this.checkExport(t3, e4.exported.name, this.lastTokStart)) : e4.exported = null), this.expectContextual("from"), this.type !== s2.string && this.unexpected(), e4.source = this.parseExprAtom(), this.type === this.assertToken) {
          this.next();
          const t4 = this.parseImportAssertions();
          t4 && (e4.assertions = t4);
        }
        return this.semicolon(), this.finishNode(e4, "ExportAllDeclaration");
      }
      if (this.eat(s2._default)) {
        var i3;
        if (this.checkExport(t3, "default", this.lastTokStart), this.type === s2._function || (i3 = this.isAsyncFunction())) {
          var n2 = this.startNode();
          this.next(), i3 && this.next(), e4.declaration = this.parseFunction(n2, 5, false, i3);
        } else if (this.type === s2._class) {
          var r2 = this.startNode();
          e4.declaration = this.parseClass(r2, "nullableID");
        } else
          e4.declaration = this.parseMaybeAssign(), this.semicolon();
        return this.finishNode(e4, "ExportDefaultDeclaration");
      }
      if (this.shouldParseExportStatement())
        e4.declaration = this.parseStatement(null), "VariableDeclaration" === e4.declaration.type ? this.checkVariableExport(t3, e4.declaration.declarations) : this.checkExport(t3, e4.declaration.id.name, e4.declaration.id.start), e4.specifiers = [], e4.source = null;
      else {
        if (e4.declaration = null, e4.specifiers = this.parseExportSpecifiers(t3), this.eatContextual("from")) {
          if (this.type !== s2.string && this.unexpected(), e4.source = this.parseExprAtom(), this.type === this.assertToken) {
            this.next();
            const t4 = this.parseImportAssertions();
            t4 && (e4.assertions = t4);
          }
        } else {
          for (var o2 = 0, a2 = e4.specifiers; o2 < a2.length; o2 += 1) {
            var l2 = a2[o2];
            this.checkUnreserved(l2.local), this.checkLocalExport(l2.local);
          }
          e4.source = null;
        }
        this.semicolon();
      }
      return this.finishNode(e4, "ExportNamedDeclaration");
    }
    parseImport(e4) {
      if (this.next(), this.type === s2.string ? (e4.specifiers = [], e4.source = this.parseExprAtom()) : (e4.specifiers = this.parseImportSpecifiers(), this.expectContextual("from"), e4.source = this.type === s2.string ? this.parseExprAtom() : this.unexpected()), this.type === this.assertToken) {
        this.next();
        const t3 = this.parseImportAssertions();
        t3 && (e4.assertions = t3);
      }
      return this.semicolon(), this.finishNode(e4, "ImportDeclaration");
    }
    parseImportAssertions() {
      this._eat(s2.braceL);
      const e4 = this.parseAssertEntries();
      return this._eat(s2.braceR), e4;
    }
    parseAssertEntries() {
      const e4 = [], t3 = /* @__PURE__ */ new Set();
      do {
        if (this.type === s2.braceR)
          break;
        const i3 = this.startNode();
        let n2;
        n2 = this.type === s2.string ? this.parseLiteral(this.value) : this.parseIdent(true), this.next(), i3.key = n2, t3.has(i3.key.name) && this.raise(this.pos, "Duplicated key in assertions"), t3.add(i3.key.name), this.type !== s2.string && this.raise(this.pos, "Only string is supported as an assertion value"), i3.value = this.parseLiteral(this.value), e4.push(this.finishNode(i3, "ImportAttribute"));
      } while (this.eat(s2.comma));
      return e4;
    }
  };
}
function Jh(e3) {
  return Array.isArray(e3) ? e3.filter(Boolean) : e3 ? [e3] : [];
}
const Zh = (e3) => console.warn(e3.message || e3);
function ec(e3, t2, s2, i2, n2 = /$./) {
  const r2 = new Set(t2), o2 = Object.keys(e3).filter((e4) => !(r2.has(e4) || n2.test(e4)));
  o2.length > 0 && i2(function(e4, t3, s3) {
    return { code: "UNKNOWN_OPTION", message: `Unknown ${e4}: ${t3.join(", ")}. Allowed options: ${s3.join(", ")}` };
  }(s2, o2, [...r2].sort()));
}
const tc = { recommended: { annotations: true, correctVarValueBeforeDeclaration: false, manualPureFunctions: fe, moduleSideEffects: () => true, propertyReadSideEffects: true, tryCatchDeoptimization: true, unknownGlobalSideEffects: false }, safest: { annotations: true, correctVarValueBeforeDeclaration: true, manualPureFunctions: fe, moduleSideEffects: () => true, propertyReadSideEffects: true, tryCatchDeoptimization: true, unknownGlobalSideEffects: true }, smallest: { annotations: true, correctVarValueBeforeDeclaration: false, manualPureFunctions: fe, moduleSideEffects: () => false, propertyReadSideEffects: false, tryCatchDeoptimization: false, unknownGlobalSideEffects: false } }, sc = { es2015: { arrowFunctions: true, constBindings: true, objectShorthand: true, reservedNamesAsProps: true, symbols: true }, es5: { arrowFunctions: false, constBindings: false, objectShorthand: false, reservedNamesAsProps: true, symbols: false } }, ic = (e3, t2, s2, i2, n2) => {
  const r2 = e3 == null ? void 0 : e3.preset;
  if (r2) {
    const n3 = t2[r2];
    if (n3)
      return { ...n3, ...e3 };
    _e(Xe(`${s2}.preset`, i2, `valid values are ${be(Object.keys(t2))}`, r2));
  }
  return ((e4, t3, s3, i3) => (n3) => {
    if ("string" == typeof n3) {
      const r3 = e4[n3];
      if (r3)
        return r3;
      _e(Xe(t3, s3, `valid values are ${i3}${be(Object.keys(e4))}. You can also supply an object for more fine-grained control`, n3));
    }
    return ((e5) => e5 && "object" == typeof e5 ? e5 : {})(n3);
  })(t2, s2, i2, n2)(e3);
}, nc = async (e3) => (await async function(e4) {
  do {
    e4 = (await Promise.all(e4)).flat(1 / 0);
  } while (e4.some((e5) => e5 == null ? void 0 : e5.then));
  return e4;
}([e3])).filter(Boolean);
const rc = (e3) => {
  const { onwarn: t2 } = e3;
  return t2 ? (e4) => {
    e4.toString = () => {
      let t3 = "";
      return e4.plugin && (t3 += `(${e4.plugin} plugin) `), e4.loc && (t3 += `${O(e4.loc.file)} (${e4.loc.line}:${e4.loc.column}) `), t3 += e4.message, t3;
    }, t2(e4, Zh);
  } : Zh;
}, oc = (e3) => ({ ecmaVersion: "latest", sourceType: "module", ...e3.acorn }), ac = (e3) => [Qh, ...Jh(e3.acornInjectPlugins)], lc = (e3) => {
  var _a2;
  return true === e3.cache ? void 0 : ((_a2 = e3.cache) == null ? void 0 : _a2.cache) || e3.cache;
}, hc = (e3) => {
  if (true === e3)
    return () => true;
  if ("function" == typeof e3)
    return (t2, ...s2) => !t2.startsWith("\0") && e3(t2, ...s2) || false;
  if (e3) {
    const t2 = /* @__PURE__ */ new Set(), s2 = [];
    for (const i2 of Jh(e3))
      i2 instanceof RegExp ? s2.push(i2) : t2.add(i2);
    return (e4, ...i2) => t2.has(e4) || s2.some((t3) => t3.test(e4));
  }
  return () => false;
}, cc = (e3, t2, s2) => {
  const i2 = e3.inlineDynamicImports;
  return i2 && rt('The "inlineDynamicImports" option is deprecated. Use the "output.inlineDynamicImports" option instead.', we, true, t2, s2), i2;
}, uc = (e3) => {
  const t2 = e3.input;
  return null == t2 ? [] : "string" == typeof t2 ? [t2] : t2;
}, dc = (e3, t2, s2) => {
  const i2 = e3.manualChunks;
  return i2 && rt('The "manualChunks" option is deprecated. Use the "output.manualChunks" option instead.', Pe, true, t2, s2), i2;
}, pc = (e3, t2, s2) => {
  const i2 = e3.maxParallelFileReads;
  "number" == typeof i2 && rt('The "maxParallelFileReads" option is deprecated. Use the "maxParallelFileOps" option instead.', "configuration-options/#maxparallelfileops", true, t2, s2);
  const n2 = e3.maxParallelFileOps ?? i2;
  return "number" == typeof n2 ? n2 <= 0 ? 1 / 0 : n2 : 20;
}, fc = (e3, t2) => {
  const s2 = e3.moduleContext;
  if ("function" == typeof s2)
    return (e4) => s2(e4) ?? t2;
  if (s2) {
    const e4 = /* @__PURE__ */ Object.create(null);
    for (const [t3, i2] of Object.entries(s2))
      e4[$(t3)] = i2;
    return (s3) => e4[s3] ?? t2;
  }
  return () => t2;
}, mc = (e3, t2, s2) => {
  const i2 = e3.preserveModules;
  return i2 && rt('The "preserveModules" option is deprecated. Use the "output.preserveModules" option instead.', "configuration-options/#output-preservemodules", true, t2, s2), i2;
}, gc = (e3) => {
  if (false === e3.treeshake)
    return false;
  const t2 = ic(e3.treeshake, tc, "treeshake", "configuration-options/#treeshake", "false, true, ");
  return { annotations: false !== t2.annotations, correctVarValueBeforeDeclaration: true === t2.correctVarValueBeforeDeclaration, manualPureFunctions: t2.manualPureFunctions ?? fe, moduleSideEffects: yc(t2.moduleSideEffects), propertyReadSideEffects: "always" === t2.propertyReadSideEffects ? "always" : false !== t2.propertyReadSideEffects, tryCatchDeoptimization: false !== t2.tryCatchDeoptimization, unknownGlobalSideEffects: false !== t2.unknownGlobalSideEffects };
}, yc = (e3) => {
  if ("boolean" == typeof e3)
    return () => e3;
  if ("no-external" === e3)
    return (e4, t2) => !t2;
  if ("function" == typeof e3)
    return (t2, s2) => !!t2.startsWith("\0") || false !== e3(t2, s2);
  if (Array.isArray(e3)) {
    const t2 = new Set(e3);
    return (e4) => t2.has(e4);
  }
  return e3 && _e(Xe("treeshake.moduleSideEffects", "configuration-options/#treeshake-modulesideeffects", 'please use one of false, "no-external", a function or an array')), () => true;
}, xc = /[\u0000-\u001F"#$&*+,:;<=>?[\]^`{|}\u007F]/g, bc = /^[a-z]:/i;
function Ec(e3) {
  const t2 = bc.exec(e3), s2 = t2 ? t2[0] : "";
  return s2 + e3.slice(s2.length).replace(xc, "_");
}
const vc = (e3, t2, s2) => {
  const { file: i2 } = e3;
  if ("string" == typeof i2) {
    if (t2)
      return _e(Xe("output.file", Se, 'you must set "output.dir" instead of "output.file" when using the "output.preserveModules" option'));
    if (!Array.isArray(s2.input))
      return _e(Xe("output.file", Se, 'you must set "output.dir" instead of "output.file" when providing named inputs'));
  }
  return i2;
}, Sc = (e3) => {
  const t2 = e3.format;
  switch (t2) {
    case void 0:
    case "es":
    case "esm":
    case "module":
      return "es";
    case "cjs":
    case "commonjs":
      return "cjs";
    case "system":
    case "systemjs":
      return "system";
    case "amd":
    case "iife":
    case "umd":
      return t2;
    default:
      return _e(Xe("output.format", Ae, 'Valid values are "amd", "cjs", "system", "es", "iife" or "umd"', t2));
  }
}, kc = (e3, t2) => {
  const s2 = (e3.inlineDynamicImports ?? t2.inlineDynamicImports) || false, { input: i2 } = t2;
  return s2 && (Array.isArray(i2) ? i2 : Object.keys(i2)).length > 1 ? _e(Xe("output.inlineDynamicImports", we, 'multiple inputs are not supported when "output.inlineDynamicImports" is true')) : s2;
}, Ac = (e3, t2, s2) => {
  const i2 = (e3.preserveModules ?? s2.preserveModules) || false;
  if (i2) {
    if (t2)
      return _e(Xe("output.inlineDynamicImports", we, 'this option is not supported for "output.preserveModules"'));
    if (false === s2.preserveEntrySignatures)
      return _e(Xe("preserveEntrySignatures", "configuration-options/#preserveentrysignatures", 'setting this option to false is not supported for "output.preserveModules"'));
  }
  return i2;
}, wc = (e3, t2) => {
  const s2 = e3.preferConst;
  return null != s2 && nt('The "output.preferConst" option is deprecated. Use the "output.generatedCode.constBindings" option instead.', "configuration-options/#output-generatedcode-constbindings", true, t2), !!s2;
}, Ic = (e3) => {
  const { preserveModulesRoot: t2 } = e3;
  if (null != t2)
    return $(t2);
}, Pc = (e3) => {
  const t2 = { autoId: false, basePath: "", define: "define", forceJsExtensionForImports: false, ...e3.amd };
  return (t2.autoId || t2.basePath) && t2.id ? _e(Xe("output.amd.id", ve, 'this option cannot be used together with "output.amd.autoId"/"output.amd.basePath"')) : t2.basePath && !t2.autoId ? _e(Xe("output.amd.basePath", "configuration-options/#output-amd-basepath", 'this option only works with "output.amd.autoId"')) : t2.autoId ? { autoId: true, basePath: t2.basePath, define: t2.define, forceJsExtensionForImports: t2.forceJsExtensionForImports } : { autoId: false, define: t2.define, forceJsExtensionForImports: t2.forceJsExtensionForImports, id: t2.id };
}, Cc = (e3, t2) => {
  const s2 = e3[t2];
  return "function" == typeof s2 ? s2 : () => s2 || "";
}, $c = (e3, t2) => {
  const { dir: s2 } = e3;
  return "string" == typeof s2 && "string" == typeof t2 ? _e(Xe("output.dir", Se, 'you must set either "output.file" for a single-file build or "output.dir" when generating multiple chunks')) : s2;
}, Nc = (e3, t2, s2) => {
  const i2 = e3.dynamicImportFunction;
  return i2 && (nt('The "output.dynamicImportFunction" option is deprecated. Use the "renderDynamicImport" plugin hook instead.', "plugin-development/#renderdynamicimport", true, t2), "es" !== s2 && t2.onwarn(Xe("output.dynamicImportFunction", "configuration-options/#output-dynamicimportfunction", 'this option is ignored for formats other than "es"'))), i2;
}, _c = (e3, t2) => {
  const s2 = e3.entryFileNames;
  return null == s2 && t2.add("entryFileNames"), s2 ?? "[name].js";
};
function Tc(e3, t2) {
  const s2 = e3.experimentalDeepDynamicChunkOptimization;
  return null != s2 && nt('The "output.experimentalDeepDynamicChunkOptimization" option is deprecated as Rollup always runs the full chunking algorithm now. The option should be removed.', "configuration-options/#output-experimentaldeepdynamicchunkoptimization", true, t2), s2 || false;
}
function Rc(e3, t2) {
  const s2 = e3.exports;
  if (null == s2)
    t2.add("exports");
  else if (!["default", "named", "none", "auto"].includes(s2))
    return _e({ code: Re, message: `"output.exports" must be "default", "named", "none", "auto", or left unspecified (defaults to "auto"), received "${s2}".`, url: Ee(ke) });
  return s2 || "auto";
}
const Mc = (e3, t2) => {
  const s2 = ic(e3.generatedCode, sc, "output.generatedCode", "configuration-options/#output-generatedcode", "");
  return { arrowFunctions: true === s2.arrowFunctions, constBindings: true === s2.constBindings || t2, objectShorthand: true === s2.objectShorthand, reservedNamesAsProps: false !== s2.reservedNamesAsProps, symbols: true === s2.symbols };
}, Oc = (e3, t2) => {
  if (t2)
    return "";
  const s2 = e3.indent;
  return false === s2 ? "" : s2 ?? true;
}, Dc = /* @__PURE__ */ new Set(["compat", "auto", "esModule", "default", "defaultOnly"]), Lc = (e3) => {
  const t2 = e3.interop;
  if ("function" == typeof t2) {
    const e4 = /* @__PURE__ */ Object.create(null);
    let s2 = null;
    return (i2) => null === i2 ? s2 || Vc(s2 = t2(i2)) : i2 in e4 ? e4[i2] : Vc(e4[i2] = t2(i2));
  }
  return void 0 === t2 ? () => "default" : () => Vc(t2);
}, Vc = (e3) => Dc.has(e3) ? e3 : _e(Xe("output.interop", Ie, `use one of ${Array.from(Dc, (e4) => JSON.stringify(e4)).join(", ")}`, e3)), Bc = (e3, t2, s2, i2) => {
  const n2 = e3.manualChunks || i2.manualChunks;
  if (n2) {
    if (t2)
      return _e(Xe("output.manualChunks", Pe, 'this option is not supported for "output.inlineDynamicImports"'));
    if (s2)
      return _e(Xe("output.manualChunks", Pe, 'this option is not supported for "output.preserveModules"'));
  }
  return n2 || {};
}, Fc = (e3, t2, s2) => e3.minifyInternalExports ?? (s2 || "es" === t2 || "system" === t2), zc = (e3, t2, s2) => {
  const i2 = e3.namespaceToStringTag;
  return null != i2 ? (nt('The "output.namespaceToStringTag" option is deprecated. Use the "output.generatedCode.symbols" option instead.', "configuration-options/#output-generatedcode-symbols", true, s2), i2) : t2.symbols || false;
}, jc = (e3) => {
  const { sourcemapBaseUrl: t2 } = e3;
  if (t2)
    return function(e4) {
      try {
        new URL(e4);
      } catch {
        return false;
      }
      return true;
    }(t2) ? t2 : _e(Xe("output.sourcemapBaseUrl", "configuration-options/#output-sourcemapbaseurl", `must be a valid URL, received ${JSON.stringify(t2)}`));
};
function Uc(t2) {
  return async function(t3, s2) {
    const { options: i2, unsetOptions: n2 } = await async function(t4, s3) {
      if (!t4)
        throw new Error("You must supply an options object to rollup");
      const i3 = zh("options", await nc(t4.plugins)), { options: n3, unsetOptions: r3 } = await async function(e3) {
        const t5 = /* @__PURE__ */ new Set(), s4 = e3.context ?? "undefined", i4 = rc(e3), n4 = e3.strictDeprecations || false, r4 = pc(e3, i4, n4), o3 = { acorn: oc(e3), acornInjectPlugins: ac(e3), cache: lc(e3), context: s4, experimentalCacheExpiry: e3.experimentalCacheExpiry ?? 10, experimentalLogSideEffects: e3.experimentalLogSideEffects || false, external: hc(e3.external), inlineDynamicImports: cc(e3, i4, n4), input: uc(e3), makeAbsoluteExternalsRelative: e3.makeAbsoluteExternalsRelative ?? "ifRelativeSource", manualChunks: dc(e3, i4, n4), maxParallelFileOps: r4, maxParallelFileReads: r4, moduleContext: fc(e3, s4), onwarn: i4, perf: e3.perf || false, plugins: await nc(e3.plugins), preserveEntrySignatures: e3.preserveEntrySignatures ?? "exports-only", preserveModules: mc(e3, i4, n4), preserveSymlinks: e3.preserveSymlinks || false, shimMissingExports: e3.shimMissingExports || false, strictDeprecations: n4, treeshake: gc(e3) };
        return ec(e3, [...Object.keys(o3), "watch"], "input options", o3.onwarn, /^(output)$/), { options: o3, unsetOptions: t5 };
      }(await i3.reduce(function(t5) {
        return async (s4, i4) => {
          const n4 = "handler" in i4.options ? i4.options.handler : i4.options;
          return await n4.call({ meta: { rollupVersion: e, watchMode: t5 } }, await s4) || s4;
        };
      }(s3), Promise.resolve(t4)));
      return Gc(n3.plugins, kh), { options: n3, unsetOptions: r3 };
    }(t3, null !== s2);
    !function(e3) {
      e3.perf ? (or = /* @__PURE__ */ new Map(), ur = lr, dr = hr, e3.plugins = e3.plugins.map(fr)) : (ur = rr, dr = rr);
    }(i2);
    const r2 = new qh(i2, s2), o2 = false !== t3.cache;
    t3.cache && (i2.cache = void 0, t3.cache = void 0);
    ur("BUILD", 1), await Hh(r2.pluginDriver, async () => {
      try {
        ur("initialize", 2), await r2.pluginDriver.hookParallel("buildStart", [i2]), dr("initialize", 2), await r2.build();
      } catch (e3) {
        const t4 = Object.keys(r2.watchFiles);
        throw t4.length > 0 && (e3.watchFiles = t4), await r2.pluginDriver.hookParallel("buildEnd", [e3]), await r2.pluginDriver.hookParallel("closeBundle", []), e3;
      }
      await r2.pluginDriver.hookParallel("buildEnd", []);
    }), dr("BUILD", 1);
    const a2 = { cache: o2 ? r2.getCache() : void 0, async close() {
      a2.closed || (a2.closed = true, await r2.pluginDriver.hookParallel("closeBundle", []));
    }, closed: false, generate: async (e3) => a2.closed ? _e({ code: "ALREADY_CLOSED", message: 'Bundle is already closed, no more calls to "generate" or "write" are allowed.' }) : Wc(false, i2, n2, e3, r2), watchFiles: Object.keys(r2.watchFiles), write: async (e3) => a2.closed ? _e({ code: "ALREADY_CLOSED", message: 'Bundle is already closed, no more calls to "generate" or "write" are allowed.' }) : Wc(true, i2, n2, e3, r2) };
    i2.perf && (a2.getTimings = cr);
    return a2;
  }(t2, null);
}
function Gc(e3, t2) {
  for (const [s2, i2] of e3.entries())
    i2.name || (i2.name = `${t2}${s2 + 1}`);
}
async function Wc(e3, t2, s2, i2, n2) {
  const { options: r2, outputPluginDriver: o2, unsetOptions: a2 } = await async function(e4, t3, s3, i3) {
    if (!e4)
      throw new Error("You must supply an options object");
    const n3 = await nc(e4.plugins);
    Gc(n3, Ah);
    const r3 = t3.createOutputPluginDriver(n3);
    return { ...await qc(s3, i3, e4, r3), outputPluginDriver: r3 };
  }(i2, n2.pluginDriver, t2, s2);
  return Hh(0, async () => {
    const s3 = new Pa(r2, a2, t2, o2, n2), i3 = await s3.generate(e3);
    if (e3) {
      if (ur("WRITE", 1), !r2.dir && !r2.file)
        return _e({ code: "MISSING_OPTION", message: 'You must specify "output.file" or "output.dir" for the build.', url: Ee(Se) });
      await Promise.all(Object.values(i3).map((e4) => n2.fileOperationQueue.run(() => async function(e5, t3) {
        const s4 = $(t3.dir || I(t3.file), e5.fileName);
        return await bh(I(s4), { recursive: true }), vh(s4, "asset" === e5.type ? e5.source : e5.code);
      }(e4, r2)))), await o2.hookParallel("writeBundle", [r2, i3]), dr("WRITE", 1);
    }
    return l2 = i3, { output: Object.values(l2).filter((e4) => Object.keys(e4).length > 0).sort((e4, t3) => Kc(e4) - Kc(t3)) };
    var l2;
  });
}
function qc(e3, t2, s2, i2) {
  return async function(e4, t3, s3) {
    const i3 = new Set(s3), n2 = e4.compact || false, r2 = Sc(e4), o2 = kc(e4, t3), a2 = Ac(e4, o2, t3), l2 = vc(e4, a2, t3), h2 = wc(e4, t3), c2 = Mc(e4, h2), u2 = { amd: Pc(e4), assetFileNames: e4.assetFileNames ?? "assets/[name]-[hash][extname]", banner: Cc(e4, "banner"), chunkFileNames: e4.chunkFileNames ?? "[name]-[hash].js", compact: n2, dir: $c(e4, l2), dynamicImportFunction: Nc(e4, t3, r2), dynamicImportInCjs: e4.dynamicImportInCjs ?? true, entryFileNames: _c(e4, i3), esModule: e4.esModule ?? "if-default-prop", experimentalDeepDynamicChunkOptimization: Tc(e4, t3), experimentalMinChunkSize: e4.experimentalMinChunkSize || 0, exports: Rc(e4, i3), extend: e4.extend || false, externalImportAssertions: e4.externalImportAssertions ?? true, externalLiveBindings: e4.externalLiveBindings ?? true, file: l2, footer: Cc(e4, "footer"), format: r2, freeze: e4.freeze ?? true, generatedCode: c2, globals: e4.globals || {}, hoistTransitiveImports: e4.hoistTransitiveImports ?? true, indent: Oc(e4, n2), inlineDynamicImports: o2, interop: Lc(e4), intro: Cc(e4, "intro"), manualChunks: Bc(e4, o2, a2, t3), minifyInternalExports: Fc(e4, r2, n2), name: e4.name, namespaceToStringTag: zc(e4, c2, t3), noConflict: e4.noConflict || false, outro: Cc(e4, "outro"), paths: e4.paths || {}, plugins: await nc(e4.plugins), preferConst: h2, preserveModules: a2, preserveModulesRoot: Ic(e4), sanitizeFileName: "function" == typeof e4.sanitizeFileName ? e4.sanitizeFileName : false === e4.sanitizeFileName ? (e5) => e5 : Ec, sourcemap: e4.sourcemap || false, sourcemapBaseUrl: jc(e4), sourcemapExcludeSources: e4.sourcemapExcludeSources || false, sourcemapFile: e4.sourcemapFile, sourcemapIgnoreList: "function" == typeof e4.sourcemapIgnoreList ? e4.sourcemapIgnoreList : false === e4.sourcemapIgnoreList ? () => false : (e5) => e5.includes("node_modules"), sourcemapPathTransform: e4.sourcemapPathTransform, strict: e4.strict ?? true, systemNullSetters: e4.systemNullSetters ?? true, validate: e4.validate || false };
    return ec(e4, Object.keys(u2), "output options", t3.onwarn), { options: u2, unsetOptions: i3 };
  }(i2.hookReduceArg0Sync("outputOptions", [s2], (e4, t3) => t3 || e4, (e4) => {
    const t3 = () => e4.error({ code: "CANNOT_EMIT_FROM_OPTIONS_HOOK", message: 'Cannot emit files or set asset sources in the "outputOptions" hook, use the "renderStart" hook instead.' });
    return { ...e4, emitFile: t3, setAssetSource: t3 };
  }), e3, t2);
}
var Hc;
function Kc(e3) {
  return "asset" === e3.type ? Hc.ASSET : e3.isEntry ? Hc.ENTRY_CHUNK : Hc.SECONDARY_CHUNK;
}
!function(e3) {
  e3[e3.ENTRY_CHUNK = 0] = "ENTRY_CHUNK", e3[e3.SECONDARY_CHUNK = 1] = "SECONDARY_CHUNK", e3[e3.ASSET = 2] = "ASSET";
}(Hc || (Hc = {}));
function normalizeArray(parts, allowAboveRoot) {
  const res = [];
  for (var i2 = 0; i2 < parts.length; i2++) {
    const p2 = parts[i2];
    if (!p2 || p2 === ".")
      continue;
    if (p2 === "..") {
      if (res.length && res[res.length - 1] !== "..") {
        res.pop();
      } else if (allowAboveRoot) {
        res.push("..");
      }
    } else {
      res.push(p2);
    }
  }
  return res;
}
function absolutePath(path, basePath) {
  if (path.indexOf(":") > 0)
    return path;
  if (!path.startsWith("./"))
    return path;
  let resolvedPath = path;
  if (basePath && !resolvedPath.startsWith("/")) {
    resolvedPath = basePath + "/../" + path;
  }
  resolvedPath = normalizeArray(
    resolvedPath.split("/"),
    false
  ).join("/");
  return "/" + resolvedPath;
}
async function rollupTransform(path, source) {
  if (path.endsWith(".lib"))
    path += ".js";
  const parts = absolutePath("./parts/", path);
  const inputOptions = {
    input: path,
    shimMissingExports: false,
    treeshake: false,
    external(id, importer) {
      id = absolutePath(id, importer);
      return id != path && !id.startsWith(parts);
    },
    plugins: [
      {
        name: "rollup-adapter",
        resolveId(importee, importer) {
          console.debug("resolveId:importee=" + importee + ",importer=" + importer);
          if (importee.endsWith(".lib")) {
            importee += ".js";
          }
          return absolutePath(importee, importer);
        },
        load(id) {
          console.debug("load:id=" + id);
          if (id == path) {
            return source;
          } else {
            return jsLibLoader(id);
          }
        }
      }
      // commonjs(),
      // tsPlugin
    ]
  };
  const outputOptions = {
    format: "system",
    minifyInternalExports: false,
    //preserveModules:true,
    generatedCode: "es2015",
    chunkFileNames: "[name]",
    sanitizeFileName: false,
    // 如果为true, 'nop:utils'将被替换为/nop_utils
    manualChunks(id) {
      return "app";
    }
  };
  const bundle = await Uc(inputOptions);
  const generated = await bundle.generate(outputOptions);
  let { code } = generated.output[0];
  code = code.replace(/\'\.\/@nop\/utils\.js\'/g, "'@nop/utils'");
  return code;
}
export {
  rollupTransform
};

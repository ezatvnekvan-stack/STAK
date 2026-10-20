#!/usr/bin/env node
// Dependency-free structural check. Run with --expose-internals. This is not a React build.
const acorn = require('internal/deps/acorn/acorn/dist/acorn');
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '../frontend');
const options = { ecmaVersion: 'latest', sourceType: 'module', allowHashBang: true };
function container(source, start) {
  let previous=null, depth=1, offset=start+1, tokenizer=acorn.tokenizer(source.slice(offset), options);
  for (;;) {
    const t=tokenizer.getToken(), name=t.type.label;
    if (name==='eof') throw Error('Unclosed JSX expression');
    if (t.value==='<' && (!previous || previous.type.beforeExpr) && /[A-Za-z>]/.test(source[offset+t.end]||'')) {
      const j=jsx(source,offset+t.start); offset=j.end; tokenizer=acorn.tokenizer(source.slice(offset),options); continue;
    }
    if (name==='{' || name==='${') depth++;
    if (name==='}' && --depth===0) return { body:source.slice(start+1,offset+t.start),end:offset+t.end };
    previous=t;
  }
}
function jsx(source,start) {
  let pos=start+1, expressions=[];
  const tag=(source.slice(pos).match(/^[\w.$:-]+/)||[''])[0];pos+=tag.length;
  while(pos<source.length) {
    if(source[pos]==='"'||source[pos]==="'") {const q=source[pos++];while(pos<source.length&&source[pos]!==q){if(source[pos]==='\\')pos++;pos++;}pos++;continue;}
    if(source[pos]==='{'){const c=container(source,pos);expressions.push(c.body);pos=c.end;continue;}
    if(source.startsWith('/>',pos))return{end:pos+2,expressions};
    if(source[pos]==='>'){pos++;break;}pos++;
  }
  while(pos<source.length) {
    if(source.startsWith('</',pos)) {const close=(source.slice(pos+2).match(/^[\w.$:-]+/)||[''])[0];if(close!==tag)throw Error(`JSX closing tag ${close} does not match ${tag}`);const end=source.indexOf('>',pos);if(end<0)throw Error('Unclosed JSX tag');return{end:end+1,expressions};}
    if(source[pos]==='<'){const child=jsx(source,pos);expressions.push(...child.expressions);pos=child.end;continue;}
    if(source[pos]==='{'){const c=container(source,pos);expressions.push(c.body);pos=c.end;continue;}
    pos++;
  }
  throw Error(`Unclosed JSX element ${tag}`);
}
function transform(source) {
  let previous=null, tokenizer=acorn.tokenizer(source,options);
  for (;;) {
    const t=tokenizer.getToken();if(t.type.label==='eof')break;
    if(t.value==='<' && (!previous || previous.type.beforeExpr) && /[A-Za-z>]/.test(source[t.end]||'')) {
      const block=jsx(source,t.start);
      const replacement=`__jsx(${block.expressions.filter(x => x.replace(/\/\*[\s\S]*?\*\//g, '').trim()).map(transform).join(',')})`;
      source=source.slice(0,t.start)+replacement+source.slice(block.end);tokenizer=acorn.tokenizer(source,options);previous=null;continue;
    }
    previous=t;
  }
  return source;
}
const files=[];
function walk(dir){for(const name of fs.readdirSync(dir)){const p=path.join(dir,name);if(fs.statSync(p).isDirectory())walk(p);else if(/\.(js|jsx)$/.test(name))files.push(p);}}
walk(path.join(root,'src'));
const exported = new Map();
function names(pattern){if(!pattern)return[];if(pattern.type==='Identifier')return[pattern.name];if(pattern.type==='ObjectPattern')return pattern.properties.flatMap(p=>names(p.value||p.argument));if(pattern.type==='ArrayPattern')return pattern.elements.flatMap(names);if(pattern.type==='RestElement')return names(pattern.argument);return[];}
function exportsOf(file){
  if(exported.has(file))return exported.get(file);
  const ast=acorn.parse(transform(fs.readFileSync(file,'utf8')),options), result=new Set();exported.set(file,result);
  for(const x of ast.body){if(x.type==='ExportDefaultDeclaration')result.add('default');if(x.type==='ExportNamedDeclaration'){for(const sp of x.specifiers||[])result.add(sp.exported.name);const d=x.declaration;if(d?.id)result.add(d.id.name);for(const v of d?.declarations||[])for(const name of names(v.id))result.add(name);}if(x.type==='ExportAllDeclaration')result.add('*');}
  return result;
}
let count=0;
for(const file of files){
  try {
    const text=fs.readFileSync(file,'utf8'), ast=acorn.parse(transform(text),options);
    for(const statement of ast.body){
      const specifier=statement.source?.value;
      if(!specifier || !(specifier.startsWith('.')||specifier.startsWith('@/')))continue;
      const target=specifier.startsWith('@/')?path.join(root,'src',specifier.slice(2)):path.resolve(path.dirname(file),specifier);
      const resolved=['','.js','.jsx','.css','.json','/index.js','/index.jsx'].map(ext=>target+ext).find(p=>fs.existsSync(p)&&fs.statSync(p).isFile());
      if(!resolved)throw Error(`Missing import ${specifier}`);
      if(statement.type==='ImportDeclaration'&&/\.(js|jsx)$/.test(resolved)){const exports=exportsOf(resolved);for(const sp of statement.specifiers){const name=sp.type==='ImportDefaultSpecifier'?'default':sp.type==='ImportSpecifier'?sp.imported.name:null;if(name&&!exports.has(name)&&!exports.has('*'))throw Error(`Missing export ${name} from ${specifier}`);}}
    }
    count++;
  }catch(e){console.error(path.relative(root,file)+': '+e.message);process.exitCode=1;}
}
console.log(`${count}/${files.length} source modules passed JavaScript/JSX structure and local import checks. React compilation and ESLint must still run.`);

declare namespace ಠ_ಠ.clutz.provides {
  class C {
    private noStructuralTyping_: any;
  }
}
declare namespace ಠ_ಠ.clutz.goog {
  function require(name: 'provides.C'): typeof ಠ_ಠ.clutz.provides.C;
}
declare module 'goog:provides.C' {
  import alias = ಠ_ಠ.clutz.provides.C;
  export default alias;
}
declare namespace ಠ_ಠ.clutz.provides {
  var instance : ಠ_ಠ.clutz.provides.C ;
}
declare namespace ಠ_ಠ.clutz.goog {
  function require(name: 'provides.instance'): typeof ಠ_ಠ.clutz.provides.instance;
}
declare module 'goog:provides.instance' {
  import alias = ಠ_ಠ.clutz.provides.instance;
  export default alias;
}

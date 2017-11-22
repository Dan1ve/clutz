declare namespace ಠ_ಠ.clutz.module$exports$missing$extend {
  class B extends B_Instance {
  }
  class B_Instance extends ಠ_ಠ.clutz.direct.ref.A {
  }
  class BTemplated extends BTemplated_Instance {
  }
  class BTemplated_Instance extends ಠ_ಠ.clutz.direct.ref.ATemplated < string , number > {
  }
  class D extends D_Instance {
  }
  //!! This emit is wrong and will be fixed with
  //!! https://github.com/angular/clutz/issues/551.
  class D_Instance extends ಠ_ಠ.clutz.module$exports$missing$base_C {
  }
  function E (c : ಠ_ಠ.clutz.module$exports$missing$base_C ) : void ;
  var F : ಠ_ಠ.clutz.module$exports$missing$base_C | null ;
}
declare module 'goog:missing.extend' {
  import alias = ಠ_ಠ.clutz.module$exports$missing$extend;
  export = alias;
}

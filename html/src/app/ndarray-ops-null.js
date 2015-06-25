import compile from 'cwise-compiler'

// handle null values in arrays
// ndarray-ops only provides standard argmin and argmax

/*eslint-disable */
export var nullargmin = compile({
  args:['index','array','shape'],
  pre:{
  body:'{this_v=Infinitythis_i=_inline_0_arg2_.slice(0)}',
  args:[
    {name:'_inline_0_arg0_',lvalue:false,rvalue:false,count:0},
    {name:'_inline_0_arg1_',lvalue:false,rvalue:false,count:0},
    {name:'_inline_0_arg2_',lvalue:false,rvalue:true,count:1}
    ],
  thisVars:['this_i','this_v'],
  localVars:[]},
  body:{
  body:'{if(_inline_1_arg1_ !== null && _inline_1_arg1_<this_v){this_v=_inline_1_arg1_for(var _inline_1_k=0_inline_1_k<_inline_1_arg0_.length++_inline_1_k){this_i[_inline_1_k]=_inline_1_arg0_[_inline_1_k]}}}',
  args:[
    {name:'_inline_1_arg0_',lvalue:false,rvalue:true,count:2},
    {name:'_inline_1_arg1_',lvalue:false,rvalue:true,count:2}],
  thisVars:['this_i','this_v'],
  localVars:['_inline_1_k']},
  post:{
  body:'{return this_i}',
  args:[],
  thisVars:['this_i'],
  localVars:[]}
})

export var nullargmax = compile({
  args:['index','array','shape'],
  pre:{
  body:'{this_v=-Infinitythis_i=_inline_0_arg2_.slice(0)}',
  args:[
    {name:'_inline_0_arg0_',lvalue:false,rvalue:false,count:0},
    {name:'_inline_0_arg1_',lvalue:false,rvalue:false,count:0},
    {name:'_inline_0_arg2_',lvalue:false,rvalue:true,count:1}
    ],
  thisVars:['this_i','this_v'],
  localVars:[]},
  body:{
  body:'{if(_inline_1_arg1_ !== null && _inline_1_arg1_>this_v){this_v=_inline_1_arg1_for(var _inline_1_k=0_inline_1_k<_inline_1_arg0_.length++_inline_1_k){this_i[_inline_1_k]=_inline_1_arg0_[_inline_1_k]}}}',
  args:[
    {name:'_inline_1_arg0_',lvalue:false,rvalue:true,count:2},
    {name:'_inline_1_arg1_',lvalue:false,rvalue:true,count:2}],
  thisVars:['this_i','this_v'],
  localVars:['_inline_1_k']},
  post:{
  body:'{return this_i}',
  args:[],
  thisVars:['this_i'],
  localVars:[]}
})
/*eslint-enable */
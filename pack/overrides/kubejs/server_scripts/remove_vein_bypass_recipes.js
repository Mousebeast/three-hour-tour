// Pillar enforcement: no ore worldgen — all mineral resources come from
// Create: Ore Excavation veins. Any recipe producing ore-tier output from
// non-vein input routes around the island-and-vein resource loop.
//
// Create: Ultimate Factory ships 37 recipes. 34 are decorative or terrain
// crushing with no vein overlap and are deliberately left alone. These are
// the ones that bypass:
//
//   compacting_coalblock  9x coal block + 100mB lava (heated) -> diamond @ 30%
//   crushing_netherite    nether bricks -> netherite scrap @ 0.5%
//   crushing_limestone    limestone -> quartz @ 12.5%, lapis @ 8%
//
// The mod also ships compat/tfmg_crushing_limestone, a twin gated on TFMG
// being loaded. TFMG is not in this pack, so that one is already inert — it
// is removed anyway so the bypass cannot reappear if TFMG is ever added.
//
// Decision: keep the mod, remove the recipes. Cutting a whole Create addon
// over three JSON files would cost 34 working recipes to fix 3.

ServerEvents.recipes(event => {
  const veinBypass = [
    'create_ultimate_factory:compacting_coalblock',
    'create_ultimate_factory:crushing_netherite',
    'create_ultimate_factory:crushing_limestone',
    'create_ultimate_factory:compat/tfmg_crushing_limestone'
  ]

  veinBypass.forEach(id => event.remove({ id: id }))
})

// Pillar enforcement: one vessel per player, forever.
//
// simulated:physics_assembler is the block that turns a raft into a Sable
// vessel. Each player gets exactly one, on their starting raft, and activation
// consumes it (the companion mod's AssemblerConsumer). Removing the recipe is
// what makes "exactly one" true - without it a player crafts a second, builds
// a second ship, and the pillar is decoration.
//
// It also matters for a reason that is easy to miss: an assembler can
// DISASSEMBLE a sub-level, not only assemble one. With "Primary Disassembly"
// on (config/simulated-server.toml) only the assembler that built a vessel may
// take it apart - and that one no longer exists after activation. Both halves
// are needed. Either alone leaves a way to destroy a ship.
//
// If a second vessel type is ever introduced (spec §3 leaves room for one),
// this is the line to revisit.

ServerEvents.recipes(event => {
  event.remove({ id: 'simulated:physics_assembler' })
})

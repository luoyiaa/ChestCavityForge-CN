package net.tigereye.chestcavity.util;


import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.ITag;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IndirectEntityDamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameRules;
import net.tigereye.chestcavity.ChestCavity;
import net.tigereye.chestcavity.chestcavities.ChestCavityInventory;
import net.tigereye.chestcavity.chestcavities.ChestCavityType;
import net.tigereye.chestcavity.chestcavities.instance.ChestCavityInstance;
import net.tigereye.chestcavity.chestcavities.organs.OrganData;
import net.tigereye.chestcavity.chestcavities.organs.OrganManager;
import net.tigereye.chestcavity.interfaces.ChestCavityEntity;
import net.tigereye.chestcavity.listeners.*;
import net.tigereye.chestcavity.registration.*;

import java.util.*;
import java.util.function.Consumer;

public class ChestCavityUtil {

    public static void addOrganScore(ResourceLocation id, float value, Map<ResourceLocation,Float> organScores){
        organScores.put(id,organScores.getOrDefault(id,0f)+value);
    }

    public static float applyBoneDefense(ChestCavityInstance cc, float damage){
        float boneDiff = (cc.getOrganScore(CCOrganScores.DEFENSE) - cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.DEFENSE))/4;
        return (float)(damage*Math.pow(1-ChestCavity.config.BONE_DEFENSE,boneDiff));
    }

    public static int applyBreathInWater(ChestCavityInstance cc, int oldAir, int newAir){
        //if your chest cavity is untouched or normal, we do nothing
        if(!cc.opened || ( cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.BREATH_CAPACITY) == cc.getOrganScore(CCOrganScores.BREATH_CAPACITY) &&
                cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.WATERBREATH) == cc.getOrganScore(CCOrganScores.WATERBREATH))){
            return newAir;
        }

        float airLoss = 1;
        //if you have waterbreath, you can breath underwater. Yay! This will overwrite any incoming air loss.
        float waterBreath = cc.getOrganScore(CCOrganScores.WATERBREATH);
        if(cc.owner.isSprinting()) {
            waterBreath /= 4;
        }
        if(waterBreath > 0){
            airLoss += (-2*waterBreath);
        }

        //if you don't (or you are still breath negative),
        //we check how well your lungs can hold oxygen
        if (airLoss > 0){
            if(oldAir == newAir){
                //this would indicate that resperation was a success
                airLoss = 0;
            }
            else {
                float capacity = cc.getOrganScore(CCOrganScores.BREATH_CAPACITY);
                airLoss *= (oldAir - newAir); //if you are downing at bonus speed, ok
                if (airLoss > 0) {
                    float lungRatio = 20f;
                    if (capacity != 0) {
                        lungRatio = Math.min(2 / capacity, 20f);
                    }
                    airLoss = (airLoss * lungRatio) + cc.lungRemainder;
                }
            }
        }

        cc.lungRemainder = airLoss % 1;
        int airResult = Math.min(oldAir - ((int) airLoss),cc.owner.getMaxAirSupply());
        //I don't trust vanilla to do this job right, so I will choke you myself
        if (airResult <= -20) {
            airResult = 0;
            cc.lungRemainder = 0;
            cc.owner.hurt(DamageSource.DROWN, 2.0F);
        }
        return airResult;
    }

    public static int applyBreathOnLand(ChestCavityInstance cc, int oldAir, int airGain){
        //we have to recreate breath mechanics here I'm afraid
        //if your chest cavity is untouched or normal, we do nothing

        if(!cc.opened || ( cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.BREATH_RECOVERY) == cc.getOrganScore(CCOrganScores.BREATH_RECOVERY) &&
                cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.BREATH_CAPACITY) == cc.getOrganScore(CCOrganScores.BREATH_CAPACITY) &&
                cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.WATERBREATH) == cc.getOrganScore(CCOrganScores.WATERBREATH))){
            return oldAir;
        }

        float airLoss;
        if(cc.owner.hasEffect(Effects.WATER_BREATHING) || cc.owner.hasEffect(Effects.CONDUIT_POWER)){
            airLoss = 0;
        }
        else{airLoss = 1;}


        //if you have breath, you can breath on land. Yay!
        //if in contact with water or rain apply on quarter your water breath as well
        //(so 2 gills can survive in humid conditions)
        float breath = cc.getOrganScore(CCOrganScores.BREATH_RECOVERY);
        if(cc.owner.isSprinting()) {
            breath /= 4;
        }
        if(cc.owner.isInWaterOrRain()){
            breath += cc.getOrganScore(CCOrganScores.WATERBREATH)/4;
        }
        if(breath > 0){
            airLoss += (-airGain * (breath) / 2);// + cc.lungRemainder;
        }

        //if you don't then unless you have the water breathing status effect you must hold your watery breath.
        //its also possible to not have enough breath to keep up with airLoss
        if (airLoss > 0) {
            //first, check if resperation cancels the sequence.
            int resperation = EnchantmentHelper.getRespiration(cc.owner);
            if (cc.owner.getRandom().nextInt(resperation + 1) != 0) {
                airLoss = 0;
            }
            else{
                //then, we apply our breath capacity
                float capacity = cc.getOrganScore(CCOrganScores.BREATH_CAPACITY);
                float breathRatio = 20f;
                if (capacity != 0) {
                    breathRatio = Math.min(2 / capacity, 20f);
                }
                airLoss = (airLoss * breathRatio) + cc.lungRemainder;
            }
        }
        else if(oldAir == cc.owner.getMaxAirSupply()) {
            return oldAir;
        }

        cc.lungRemainder = airLoss % 1;
        //we finally undo the air gained in vanilla while calculating final results
        int airResult = Math.min(oldAir - ((int) airLoss) - airGain,cc.owner.getMaxAirSupply());
        //I don't trust vanilla to do this job right, so I will choke you myself
        if (airResult <= -20) {
            airResult = 0;
            cc.lungRemainder = 0;
            cc.owner.hurt(DamageSource.DROWN, 2.0F);
        }
        return airResult;
    }

    public static float applyDefenses(ChestCavityInstance cc, DamageSource source, float damage){
        if(!cc.opened){
            return damage;
        }
        if(attemptArrowDodging(cc,source)){
            return 0;
        }
        if(!source.isBypassArmor()) {
            damage = applyBoneDefense(cc,damage);
        }
        if(source == DamageSource.FALL){
            damage = applyLeapingToFallDamage(cc,damage);
        }
        if(source == DamageSource.FALL || source == DamageSource.FLY_INTO_WALL){
            damage = applyImpactResistant(cc,damage);
        }
        if(source.isFire()){
            damage = applyFireResistant(cc,damage);
        }
        return damage;
    }

    public static int applyDigestion(ChestCavityInstance cc, float digestion, int hunger){
        if(digestion == 1){
            return hunger;
        }
        if(digestion < 0){
            cc.owner.addEffect(new EffectInstance(Effects.CONFUSION,(int)(-hunger*digestion*400)));
            return 0;
        }
        //sadly, in order to get saturation at all we must grant at least half a haunch of food, unless we embrace incompatibility
        return Math.max((int)(hunger*digestion),1);
        //TODO: find a use for stomachs for non-players
    }

    public static float applyFireResistant(ChestCavityInstance cc, float damage){
        float fireproof = cc.getOrganScore(CCOrganScores.FIRE_RESISTANT);
        if(fireproof > 0){
            return (float)(damage*Math.pow(1-ChestCavity.config.FIREPROOF_DEFENSE,fireproof/4));
        }
        return damage;
    }

    public static float applyImpactResistant(ChestCavityInstance cc, float damage){
        float impactResistant = cc.getOrganScore(CCOrganScores.IMPACT_RESISTANT);
        if(impactResistant > 0){
            return (float)(damage*Math.pow(1-ChestCavity.config.IMPACT_DEFENSE,impactResistant/4));
        }
        return damage;
    }


    public static Float applyLeaping(ChestCavityInstance cc, float velocity) {
        float leaping = cc.getOrganScore(CCOrganScores.LEAPING);
        float defaultLeaping = cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.LEAPING);
        return velocity * Math.max(0,1+((leaping-defaultLeaping)*.25f));
    }

    public static float applyLeapingToFallDamage(ChestCavityInstance cc, float damage){
        float leapingDiff = cc.getOrganScore(CCOrganScores.LEAPING) - cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.LEAPING);
        if(leapingDiff > 0) {
            return Math.max(0, damage - (leapingDiff*leapingDiff/4));
        }
        return damage;
    }

    public static float applyNutrition(ChestCavityInstance cc, float nutrition, float saturation){
        if(nutrition == 4){
            return saturation;
        }
        if(nutrition < 0){
            cc.owner.addEffect(new EffectInstance(Effects.HUNGER,(int)(saturation*nutrition*800)));
            return 0;
        }
        return saturation*nutrition/4;
        //TODO: find a use for intestines for non-players
    }

    public static float applyNervesToMining(ChestCavityInstance cc, float miningProgress){
        float defaultNerves = cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.NERVES);
        if(defaultNerves == 0){
            return miningProgress;
        }
        float NervesDiff = (cc.getOrganScore(CCOrganScores.NERVES) - cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.NERVES));
        return miningProgress * (1+(ChestCavity.config.NERVES_HASTE * NervesDiff));
    }

    public static int applySpleenMetabolism(ChestCavityInstance cc, int foodStarvationTimer){
        if(!cc.opened){
            return foodStarvationTimer;
        }
        float metabolismDiff = cc.getOrganScore(CCOrganScores.METABOLISM)-cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.METABOLISM);
        if(metabolismDiff == 0){
            return foodStarvationTimer;
        }

        if(metabolismDiff > 0){
            cc.metabolismRemainder += metabolismDiff;
            foodStarvationTimer += (int)cc.metabolismRemainder;
        }
        else{// metabolismDiff < 0
            cc.metabolismRemainder += 1 - 1/((-metabolismDiff)+1);
            foodStarvationTimer -= (int)cc.metabolismRemainder;
        }
        cc.metabolismRemainder = cc.metabolismRemainder % 1;
        return foodStarvationTimer;
        //TODO: find a use for spleens for non-players
    }

    public static float applySwimSpeedInWater(ChestCavityInstance cc) {
        if(!cc.opened || !cc.owner.isInWater()){return 1;}
        float speedDiff = cc.getOrganScore(CCOrganScores.SWIM_SPEED) - cc.getChestCavityType().getDefaultOrganScore(CCOrganScores.SWIM_SPEED);
        if(speedDiff == 0){return 1;}
        else{
            return Math.max(0,1+(speedDiff*ChestCavity.config.SWIMSPEED_FACTOR/8));
        }

    }

    public static boolean attemptArrowDodging(ChestCavityInstance cc, DamageSource source){
        float dodge = cc.getOrganScore(CCOrganScores.ARROW_DODGING);
        if(dodge == 0){
            return false;
        }
        if(cc.owner.hasEffect(CCStatusEffects.ARROW_DODGE_COOLDOWN.get())){
            return false;
        }
        if (!(source instanceof IndirectEntityDamageSource)) {
            return false;
        }
        if(!CommonOrganUtil.teleportRandomly(cc.owner,ChestCavity.config.ARROW_DODGE_DISTANCE/dodge)){
            return false;
        }
        cc.owner.addEffect(new EffectInstance(CCStatusEffects.ARROW_DODGE_COOLDOWN.get(), (int) (ChestCavity.config.ARROW_DODGE_COOLDOWN/dodge), 0, false, false, true));
        return true;
    }

    public static void clearForbiddenSlots(ChestCavityInstance cc) {
        try {
            cc.inventory.removeListener(cc);
        } catch(NullPointerException ignored){}
        for(int i = 0; i < cc.inventory.getContainerSize();i++){
            if(cc.getChestCavityType().isSlotForbidden(i)){
                cc.owner.spawnAtLocation(cc.inventory.removeItemNoUpdate(i));
            }
        }
        cc.inventory.addListener(cc);
    }

    public static void destroyOrgansWithKey(ChestCavityInstance cc, ResourceLocation organ){
        for (int i = 0; i < cc.inventory.getContainerSize(); i++)
        {
            ItemStack slot = cc.inventory.getItem(i);
            if (slot != null && slot != ItemStack.EMPTY)
            {
                OrganData organData = lookupOrgan(slot,cc.getChestCavityType());
                if(organData != null && organData.organScores.containsKey(organ)){
                    cc.inventory.removeItemNoUpdate(i);
                }
            }
        }
        cc.inventory.setChanged();
    }

    public static boolean determineDefaultOrganScores(ChestCavityType chestCavityType) {
        Map<ResourceLocation,Float> organScores = chestCavityType.getDefaultOrganScores();
        chestCavityType.loadBaseOrganScores(organScores);
        try {
            for (int i = 0; i < chestCavityType.getDefaultChestCavity().getContainerSize(); i++) {
                ItemStack itemStack = chestCavityType.getDefaultChestCavity().getItem(i);
                if (itemStack != null && itemStack != ItemStack.EMPTY) {
                    Item slotitem = itemStack.getItem();

                    OrganData data = lookupOrgan(itemStack,chestCavityType);
                    if (data != null) {
                        data.organScores.forEach((key, value) ->
                                addOrganScore(key, value * Math.min(((float)itemStack.getCount()) / itemStack.getMaxStackSize(),1),organScores)
                        );
                    }

                }
            }
        }
        catch(IllegalStateException e){
            ChestCavity.LOGGER.warn(e.getMessage()+"[Chest Cavity] 稍后将尝试计算此默认器官评分");
            return false;
        }
        return true;
    }

    public static void drawOrgansFromPile(List<ItemStack> organPile, int rolls, Random random, List<ItemStack> loot){
        for (int i = 0; i < rolls; i++) {
            if(organPile.isEmpty()){
                break;
            }
            int roll = random.nextInt(organPile.size());
            int count = 1;
            ItemStack rolledItem = organPile.remove(roll).copy();
            if (rolledItem.getCount() > 1) {
                count += random.nextInt(rolledItem.getMaxStackSize());
            }
            rolledItem.setCount(count);
            loot.add(rolledItem);
        }
    }

    public static void dropUnboundOrgans(ChestCavityInstance cc) {
        try {
            cc.inventory.removeListener(cc);
        } catch(NullPointerException ignored){}
        for(int i = 0; i < cc.inventory.getContainerSize(); i++){
            ItemStack itemStack = cc.inventory.getItem(i);
            if(itemStack != null && itemStack != ItemStack.EMPTY) {
                int compatibility = getCompatibilityLevel(cc,itemStack);
                if(compatibility < 2){
                    cc.owner.spawnAtLocation(cc.inventory.removeItemNoUpdate(i));
                }
            }
        }
        cc.inventory.addListener(cc);
        evaluateChestCavity(cc);
    }

    public static void evaluateChestCavity(ChestCavityInstance cc) {
        Map<ResourceLocation,Float> organScores = cc.getOrganScores();
        if(!cc.opened){
            organScores.clear();
            if(cc.getChestCavityType().getDefaultOrganScores() != null) {
                organScores.putAll(cc.getChestCavityType().getDefaultOrganScores());
            }
        }
        else {
            cc.onHitListeners.clear();
            cc.getChestCavityType().loadBaseOrganScores(organScores);

            for (int i = 0; i < cc.inventory.getContainerSize(); i++) {
                ItemStack itemStack = cc.inventory.getItem(i);
                if (itemStack != null && itemStack != ItemStack.EMPTY) {
                    Item slotitem = itemStack.getItem();

                    OrganData data = lookupOrgan(itemStack,cc.getChestCavityType());
                    if (data != null) {
                        data.organScores.forEach((key, value) ->
                                addOrganScore(key, value * Math.min(((float)itemStack.getCount()) / itemStack.getMaxStackSize(),1),organScores)
                        );
                        if(slotitem instanceof OrganOnHitListener){
                            cc.onHitListeners.add(new OrganOnHitContext(itemStack,(OrganOnHitListener)slotitem));
                        }
                        if (!data.pseudoOrgan) {
                            int compatibility = getCompatibilityLevel(cc,itemStack);
                            if(compatibility < 1){
                                addOrganScore(CCOrganScores.INCOMPATIBILITY, 1, organScores);
                            }
                        }
                    }

                }
            }
        }
        organUpdate(cc);
    }

    public static void forcefullyAddStack(ChestCavityInstance cc, ItemStack stack, int slot){
        if(!cc.inventory.canAddItem(stack)) {
            if (cc.owner.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && cc.owner instanceof PlayerEntity) {
                if (!((PlayerEntity) cc.owner).inventory.add(stack)) {
                    cc.owner.spawnAtLocation(cc.inventory.removeItemNoUpdate(slot));
                }
            } else {
                cc.owner.spawnAtLocation(cc.inventory.removeItemNoUpdate(slot));
            }
        }
        cc.inventory.addItem(stack);
    }

    public static void generateChestCavityIfOpened(ChestCavityInstance cc){
        if(cc.opened) {
            cc.inventory.readTags(cc.getChestCavityType().getDefaultChestCavity().getTags());
            cc.getChestCavityType().setOrganCompatibility(cc);
        }
    }

    public static int getCompatibilityLevel(ChestCavityInstance cc, ItemStack itemStack){
        if(itemStack != null && itemStack != ItemStack.EMPTY) {
            if(EnchantmentHelper.getItemEnchantmentLevel(CCEnchantments.MALPRACTICE.get(),itemStack)>0){
                return 0;
            }
            int oNegative = EnchantmentHelper.getItemEnchantmentLevel(CCEnchantments.O_NEGATIVE.get(),itemStack);
            int ownership = 0;
            CompoundNBT tag = itemStack.getTag();
            if (tag != null && tag.contains(ChestCavity.COMPATIBILITY_TAG.toString())) {
                tag = tag.getCompound(ChestCavity.COMPATIBILITY_TAG.toString());
                if (tag.getUUID("owner").equals(cc.compatibility_id)) {
                    ownership = 2;
                }
            } else {
                ownership = 1;
            }
            return Math.max(oNegative,ownership);
        }
        return 1;
    }

    public static void insertWelfareOrgans(ChestCavityInstance cc){
        //urgently essential organs are: heart, spine, lung, and just a touch of strength
        if(cc.getOrganScore(CCOrganScores.HEALTH) <= 0){
            forcefullyAddStack(cc, new ItemStack(CCItems.ROTTEN_HEART.get()),4);
        }
        if(cc.getOrganScore(CCOrganScores.BREATH_RECOVERY) <= 0){
            forcefullyAddStack(cc, new ItemStack(CCItems.ROTTEN_LUNG.get()),3);
        }
        if(cc.getOrganScore(CCOrganScores.NERVES) <= 0){
            forcefullyAddStack(cc, new ItemStack(CCItems.ROTTEN_SPINE.get()),13);
        }
        if(cc.getOrganScore(CCOrganScores.STRENGTH) <= 0){
            forcefullyAddStack(cc, new ItemStack(Items.ROTTEN_FLESH,16),0);
        }
    }

    public static boolean isHydroPhobicOrAllergic(LivingEntity entity){
        Optional<ChestCavityEntity> optional = ChestCavityEntity.of(entity);
        if(optional.isPresent()){
            ChestCavityInstance cc = optional.get().getChestCavityInstance();
            return (cc.getOrganScore(CCOrganScores.HYDROALLERGENIC) > 0) || (cc.getOrganScore(CCOrganScores.HYDROPHOBIA) > 0);
        }
        return false;
    }

    protected static OrganData lookupOrgan(ItemStack itemStack, ChestCavityType cct){
        OrganData organData = cct.catchExceptionalOrgan(itemStack);
        if(organData != null){ //check for exceptional organs
            return organData;
        }
        else if(OrganManager.hasEntry(itemStack.getItem())){ //check for normal organs
            return OrganManager.getEntry(itemStack.getItem());
        }
        else{ //check for tag organs
            for (ITag.INamedTag<Item> itemTag:
                    CCTagOrgans.tagMap.keySet()) {
                if(itemTag.contains(itemStack.getItem())){
                    organData = new OrganData();
                    organData.pseudoOrgan = true;
                    organData.organScores = CCTagOrgans.tagMap.get(itemTag);
                    return organData;
                }
            }
        }
        return null;
    }

    public static EffectInstance onAddStatusEffect(ChestCavityInstance cc, EffectInstance effect) {
        return OrganAddStatusEffectCallback.organAddMobEffect(cc.owner, effect);
    }

    public static void onDeath(ChestCavityEntity entity){
        ChestCavityInstance ccinstance = entity.getChestCavityInstance();
        ccinstance.getChestCavityType().onDeath(ccinstance);
        if(entity instanceof PlayerEntity){
            PlayerEntity playerEntity = (PlayerEntity) entity;
            if(!ChestCavity.config.KEEP_CHEST_CAVITY) {
                Map<Integer,ItemStack> organsToKeep = new HashMap<>();
                for (int i = 0; i < ccinstance.inventory.getContainerSize(); i++) {
                    ItemStack organ = ccinstance.inventory.getItem(i);
                    if(EnchantmentHelper.getItemEnchantmentLevel(CCEnchantments.O_NEGATIVE.get(),organ) >= 2){
                        organsToKeep.put(i,organ.copy());
                    }
                }
                ccinstance.compatibility_id = UUID.randomUUID();
                generateChestCavityIfOpened(ccinstance);
                for (Map.Entry<Integer,ItemStack> entry: organsToKeep.entrySet()) {
                    ccinstance.inventory.setItem(entry.getKey(),entry.getValue());
                }
            }
            insertWelfareOrgans(ccinstance);
        }
    }

    public static float onHit(ChestCavityInstance cc, DamageSource source, LivingEntity target, float damage){
        if(cc.opened) {
            //this is for individual organs
            for (OrganOnHitContext e:
                    cc.onHitListeners) {
                damage = e.listener.onHit(source,cc.owner,target,cc,e.organ,damage);
            }
            //this is for organ scores
            //OrganOnHitCallback.EVENT.invoker().onHit(source,cc.owner,target,cc,damage);
            organUpdate(cc);
        }
        return damage;
    }

    public static void onTick(ChestCavityInstance cc){
        if(cc.updateInstantiated) {
            NetworkUtil.SendS2CChestCavityUpdatePacket(cc);
        }
        if(cc.opened) {
            OrganTickCallback.organTick(cc.owner, cc);
            organUpdate(cc);
        }
    }

    public static ChestCavityInventory openChestCavity(ChestCavityInstance cc){
        if(!cc.opened) {
            try {
                cc.inventory.removeListener(cc);
            }
            catch(NullPointerException ignored){}
            cc.opened = true;
            generateChestCavityIfOpened(cc);
            cc.inventory.addListener(cc);
        }
        return cc.inventory;
    }

    public static void organUpdate(ChestCavityInstance cc){
        Map<ResourceLocation,Float> organScores = cc.getOrganScores();
        if(!cc.oldOrganScores.equals(organScores))
        {
            if(ChestCavity.isDebugMode() && cc.owner instanceof PlayerEntity) {
                ChestCavityUtil.outputOrganScoresString(System.out::println,cc);
            }
            OrganUpdateCallback.organUpdate(cc.owner, cc);
            cc.oldOrganScores.clear();
            cc.oldOrganScores.putAll(organScores);
            NetworkUtil.SendS2CChestCavityUpdatePacket(cc);
        }
    }

    public static void outputOrganScoresString(Consumer<String> output, ChestCavityInstance cc){
        try {
            ITextComponent name = cc.owner.getDisplayName();
            output.accept("%5BChest%20Cavity%5D%20\u663E\u793A%20" + name.getString() +"\u7684\u5668\u5B98\u8BC4\u5206%3A");
        }
        catch(Exception e){
            output.accept("%5BChest%20Cavity%5D%20\u663E\u793A\u5668\u5B98\u8BC4\u5206%3A");
        }
        cc.getOrganScores().forEach((key, value) ->
                output.accept(key.getPath() + ": " + value + " "));
    }

    public static void splashHydrophobicWithWater(PotionEntity splash){
        AxisAlignedBB box = splash.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<LivingEntity> list = splash.level.getEntitiesOfClass(LivingEntity.class, box, ChestCavityUtil::isHydroPhobicOrAllergic);
        if (!list.isEmpty()) {
            for(LivingEntity livingEntity:list) {
                double d = splash.distanceToSqr(livingEntity);
                if(d < 16.0D) {
                    Optional<ChestCavityEntity> optional = ChestCavityEntity.of(livingEntity);
                    if (optional.isPresent()) {
                        ChestCavityInstance cc = optional.get().getChestCavityInstance();
                        float allergy = cc.getOrganScore(CCOrganScores.HYDROALLERGENIC);
                        float phobia = cc.getOrganScore(CCOrganScores.HYDROPHOBIA);
                        if (allergy > 0) {
                            livingEntity.hurt(DamageSource.indirectMagic(livingEntity, splash.getOwner()), allergy/26);
                        }
                        if (phobia > 0) {
                            CommonOrganUtil.teleportRandomly(livingEntity,phobia*32);
                        }
                    }
                }
            }
        }
    }
}

/*
 * SpecialAbility.java
 * 
 * Copyright (c) 2009 Jay Lawson <jaylawson39 at yahoo.com>. All rights reserved.
 * 
 * This file is part of MekHQ.
 * 
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MekHQ.  If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.campaign.personnel;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import megamek.common.Compute;
import megamek.common.EquipmentType;
import megamek.common.TechConstants;
import megamek.common.WeaponType;
import megamek.common.weapons.BayWeapon;
import megamek.common.weapons.InfantryAttack;
import megamek.common.weapons.infantry.InfantryWeapon;
import mekhq.MekHQ;
import mekhq.MekHqXmlSerializable;
import mekhq.Utilities;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This object will serve as a wrapper for a specific pilot special ability. In the actual
 * person object we will use PilotOptions (and maybe at some point NonPilotOptions), so these
 * objects will not get written to actual personnel. Instead, we will we will keep track of a full static 
 * hash of SPAs that will contain important information on XP costs and pre-reqs that can be
 * looked up to see if a person is eligible for a particular option. All of this
 * will be customizable via an external XML file that can be user selected in the campaign
 * options (and possibly user editable).
 * 
 * @author Jay Lawson <jaylawson39 at yahoo.com>
 */
public class SpecialAbility implements MekHqXmlSerializable {

    private static Hashtable<String, SpecialAbility> specialAbilities;
    
    private String lookupName;
    private int xpCost;
    
    //this determines how much weight to give this SPA when creating new personnel
    private int weight;
    
    //prerequisite skills and options
    private Vector<String> prereqAbilities;
    private Vector<SkillPrereq> prereqSkills;
    
    //these are abilities that will disqualify the person from getting the current ability
    private Vector<String> invalidAbilities;
    
    //these are abilities that should be removed if the person gets this ability
    //(typically this is a lower value ability on the same chain (e.g. Cluster Hitter removed when you get Cluster Master)
    private Vector<String> removeAbilities;

    
    SpecialAbility() {
        this("unknown");
    }
    
    SpecialAbility(String name) {
        lookupName = name;
        prereqAbilities = new Vector<String>();
        invalidAbilities = new Vector<String>();
        removeAbilities = new Vector<String>();
        prereqSkills = new Vector<SkillPrereq>();
        xpCost = 0;
        weight = 1;
    }
    
    public boolean isEligible(Person p) {
        for(SkillPrereq sp : prereqSkills) {
            if(!sp.qualifies(p)) {
                return false;
            }
        }
        for(String ability : prereqAbilities) {
            //TODO: will this work for choice options like weapon specialist?
            if(!p.getOptions().booleanOption(ability)) {
                return false;
            }    
        } 
        for(String ability : invalidAbilities) {
            //TODO: will this work for choice options like weapon specialist?
            if(p.getOptions().booleanOption(ability)) {
                return false;
            }    
        } 
        return true;
    }
    
    public String getName() {
        return lookupName;
    }
    
    public int getCost() {
        return xpCost;
    }
    
    public int getWeight() {
        return weight;
    }
    
    public Vector<String> getRemovedAbilities() {
        return removeAbilities;
    }
    
    @Override
    public void writeToXml(PrintWriter pw1, int indent) {
        
        
    }
    
    
    public static void generateInstanceFromXML(Node wn) {
        SpecialAbility retVal = null;
        
        try {       
            retVal = new SpecialAbility();
            NodeList nl = wn.getChildNodes();
                
            for (int x=0; x<nl.getLength(); x++) {
                Node wn2 = nl.item(x);
                if (wn2.getNodeName().equalsIgnoreCase("lookupName")) {
                    retVal.lookupName = wn2.getTextContent();
                } else if (wn2.getNodeName().equalsIgnoreCase("xpCost")) {
                    retVal.xpCost = Integer.parseInt(wn2.getTextContent());        
                } else if (wn2.getNodeName().equalsIgnoreCase("weight")) {
                    retVal.weight = Integer.parseInt(wn2.getTextContent());
                } else if (wn2.getNodeName().equalsIgnoreCase("prereqAbilities")) {
                    retVal.prereqAbilities = Utilities.splitString(wn2.getTextContent(), "::");
                } else if (wn2.getNodeName().equalsIgnoreCase("invalidAbilities")) {
                    retVal.invalidAbilities = Utilities.splitString(wn2.getTextContent(), "::");
                } else if (wn2.getNodeName().equalsIgnoreCase("removeAbilities")) {
                    retVal.removeAbilities = Utilities.splitString(wn2.getTextContent(), "::");
                } else if (wn2.getNodeName().equalsIgnoreCase("skillPrereq")) {
                    SkillPrereq skill = SkillPrereq.generateInstanceFromXML(wn2);
                    if(!skill.isEmpty()) {
                        retVal.prereqSkills.add(skill);
                    }
                } 
            }       
        } catch (Exception ex) {
            // Errrr, apparently either the class name was invalid...
            // Or the listed name doesn't exist.
            // Doh!
            MekHQ.logError(ex);
        }
        specialAbilities.put(retVal.lookupName, retVal);
    }
    
    public static void initializeSPA() {
        specialAbilities = new Hashtable<String, SpecialAbility>();
        MekHQ.logMessage("Starting load of special abilities from XML...");
        // Initialize variables.
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document xmlDoc = null;
    
        
        try {
            FileInputStream fis = new FileInputStream("data/spa/default.xml");
            // Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
    
            // Parse using builder to get DOM representation of the XML file
            xmlDoc = db.parse(fis);
        } catch (Exception ex) {
            MekHQ.logError(ex);
        }
    
        Element spaEle = xmlDoc.getDocumentElement();
        NodeList nl = spaEle.getChildNodes();
    
        // Get rid of empty text nodes and adjacent text nodes...
        // Stupid weird parsing of XML.  At least this cleans it up.
        spaEle.normalize(); 
    
        // Okay, lets iterate through the children, eh?
        for (int x = 0; x < nl.getLength(); x++) {
            Node wn = nl.item(x);
    
            if (wn.getParentNode() != spaEle)
                continue;
    
            int xc = wn.getNodeType();
    
            if (xc == Node.ELEMENT_NODE) {
                // This is what we really care about.
                // All the meat of our document is in this node type, at this
                // level.
                // Okay, so what element is it?
                String xn = wn.getNodeName();
    
                if (xn.equalsIgnoreCase("ability")) {
                    generateInstanceFromXML(wn);                  
                }
            }
        }   
        MekHQ.logMessage("Done loading SPAs");
    }
    
    public static SpecialAbility getAbility(String name) {
        return specialAbilities.get(name);
    }
    
    public static String chooseWeaponSpecialization(int type, boolean isClan, int techLvl, int year) {
        ArrayList<String> candidates = new ArrayList<String>();
        for (Enumeration<EquipmentType> e = EquipmentType.getAllTypes(); e.hasMoreElements();) {
            EquipmentType et = e.nextElement();
            if(!(et instanceof WeaponType)) {
                continue;
            }
            if(et instanceof InfantryWeapon 
                    || et instanceof BayWeapon
                    || et instanceof InfantryAttack) {
                continue;
            }
            WeaponType wt = (WeaponType)et;
            if(wt.isCapital() 
                    || wt.isSubCapital() 
                    || wt.hasFlag(WeaponType.F_INFANTRY)
                    || wt.hasFlag(WeaponType.F_ONESHOT)
                    || wt.hasFlag(WeaponType.F_PROTOTYPE)) {
                continue;
            }
            if(!((wt.hasFlag(WeaponType.F_MECH_WEAPON) && type == Person.T_MECHWARRIOR) 
                    || (wt.hasFlag(WeaponType.F_AERO_WEAPON) && type != Person.T_AERO_PILOT)
                    || (wt.hasFlag(WeaponType.F_TANK_WEAPON) && !(type == Person.T_VEE_GUNNER 
                            || type == Person.T_NVEE_DRIVER 
                            || type == Person.T_GVEE_DRIVER 
                            || type == Person.T_VTOL_PILOT))
                    || (wt.hasFlag(WeaponType.F_BA_WEAPON) && type != Person.T_BA)
                    || (wt.hasFlag(WeaponType.F_PROTO_WEAPON) && type != Person.T_PROTO_PILOT))) {
                continue;
            }
            if(wt.getAtClass() == WeaponType.CLASS_NONE ||
                    wt.getAtClass() == WeaponType.CLASS_POINT_DEFENSE ||
                    wt.getAtClass() >= WeaponType.CLASS_CAPITAL_LASER) {
                continue;
            }
            if(TechConstants.isClan(wt.getTechLevel(year)) != isClan) {
                continue;
            }
            int lvl = wt.getTechLevel(year);
            if(lvl < 0) {
                continue;
            }
            if(techLvl < Utilities.getSimpleTechLevel(lvl)) {
                continue;
            }          
            if(techLvl == TechConstants.T_IS_UNOFFICIAL) {
                continue;
            }
            int ntimes = 10;
            if(techLvl >= TechConstants.T_IS_ADVANCED) {
                ntimes = 1;
            }
            while(ntimes > 0) {
                candidates.add(et.getName());
                ntimes--;
            }
        }
        if(candidates.isEmpty()) {
            return "??";
        }
        return candidates.get(Compute.randomInt(candidates.size()));
    }
    
    //TODO: also put some static methods here that return the available options for a given SPA, so
    //we can take that out of the GUI code
    
}